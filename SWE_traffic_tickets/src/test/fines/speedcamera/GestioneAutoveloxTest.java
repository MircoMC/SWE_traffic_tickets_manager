package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import fines.app.AdjustableClock;
import fines.speedcamera.Location;
import fines.domain.Detection;
import fines.users.Administrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La history: passato in sola lettura, futuro programmabile solo tramite la capability dell'amministratore. */
class GestioneAutoveloxTest {

    private final Location pos = new Location("Via Roma 1", 43.77, 11.25);
    private AdjustableClock clock;
    private Instant ora;
    private ExternalMapper esterno;
    private CachedSpeedCameraMap cache;
    private SpeedCameraManagement gestione;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        ora = clock.instant();
        esterno = new ExternalMapper(clock);
        esterno.load("A1", "2020-01-01T00:00:00Z", "50", "43.77", "11.25", "Via Roma 1");
        cache = new CachedSpeedCameraMap(new SpeedCameraMapAdapter(esterno), clock);
        gestione = new SpeedCameraServices(cache, clock).managementFor(new Administrator(4, "admin"));
    }

    private Optional<Configuration> allIstante(Instant t) {
        return Period.configurationAt(gestione.history("A1"), t);
    }

    @Test
    void unCambioFuturoSiProgrammaEVieneSubitoVistoAncheConLaCachePiena() {
        assertEquals(1, gestione.history("A1").size());                       // la cache ora è piena
        Instant tra2Ore = ora.plus(Duration.ofHours(2));
        gestione.schedule("A1", tra2Ore, 70, pos);

        assertEquals(2, gestione.history("A1").size());                       // senza aspettare un giorno né un refresh
        assertEquals(50, allIstante(ora).get().getLimit());
        assertEquals(70, allIstante(tra2Ore).get().getLimit());
        assertEquals(pos, allIstante(tra2Ore).get().getLocation());          // la posizione fa il giro completo (formato esterno e ritorno)
    }

    @Test
    void ilPassatoEIlPresenteSonoRifiutatiELaStoriaNonCambia() {
        List<Period> prima = new ArrayList<>(gestione.history("A1"));
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("A1", ora.minus(Duration.ofDays(1)), 200, pos));
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("A1", ora, 200, pos));
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("A1", Instant.parse("2020-01-01T00:00:00Z"), 200, pos));

        assertEquals(prima.size(), gestione.history("A1").size());
        assertEquals(50, allIstante(ora).get().getLimit());
        assertEquals(1, esterno.read("A1").size());
    }

    @Test
    void laCapabilityRifiutaIlPassatoAncheSeIlServizioSottostanteLoAccettasse() {
        List<String> scritture = new ArrayList<>();
        SpeedCameraMap permissivo = new SpeedCameraMap() {        // un servizio che non controlla niente
            @Override public List<Period> history(String id) { return Collections.emptyList(); }
            @Override public void schedule(String id, Instant dal, Configuration c) { scritture.add(id + "@" + dal); }
        };
        SpeedCameraManagement g = new SpeedCameraServices(new CachedSpeedCameraMap(permissivo, clock), clock)
            .managementFor(new Administrator(4, "admin"));

        assertThrows(IllegalArgumentException.class, () -> g.schedule("A1", ora.minus(Duration.ofDays(30)), 70, pos));
        assertThrows(IllegalArgumentException.class, () -> g.schedule("A1", ora, 70, pos));
        assertTrue(scritture.isEmpty());

        g.schedule("A1", ora.plusSeconds(1), 70, pos);
        assertEquals(1, scritture.size());
    }

    @Test
    void unCambioGiaProgrammatoNonSiSovrascrive() {
        Instant cambio = ora.plus(Duration.ofDays(2));
        gestione.schedule("A1", cambio, 70, pos);
        assertThrows(IllegalStateException.class, () -> gestione.schedule("A1", cambio, 130, pos));
        assertEquals(70, allIstante(cambio).get().getLimit());
    }

    @Test
    void scrivereNelFuturoNonCambiaMaiNessunIstanteDelPassato() {
        List<Instant> campioni = new ArrayList<>();
        for (Instant t = ora.minus(Duration.ofDays(400)); !t.isAfter(ora); t = t.plus(Duration.ofHours(6))) {
            campioni.add(t);
        }
        List<Optional<Configuration>> prima = new ArrayList<>();
        for (Instant t : campioni) prima.add(allIstante(t));

        Random caso = new Random(42);
        int riusciti = 0, rifiutati = 0;
        for (int i = 0; i < 80; i++) {
            try {
                gestione.schedule("A1", ora.plus(Duration.ofMinutes(1 + caso.nextInt(60 * 24 * 90))), 1 + caso.nextInt(130), pos);
                riusciti++;
            } catch (IllegalStateException giaProgrammato) {
                rifiutati++;
            }
            try {                                        // e ogni volta un tentativo nel passato
                gestione.schedule("A1", ora.minus(Duration.ofMinutes(caso.nextInt(60 * 24 * 400))), 1 + caso.nextInt(130), pos);
                fail("una scrittura nel passato è stata accettata");
            } catch (IllegalArgumentException atteso) {
                rifiutati++;
            }
        }
        assertTrue(riusciti > 10);
        for (int i = 0; i < campioni.size(); i++) {
            assertEquals(prima.get(i), allIstante(campioni.get(i)));
        }
    }

    @Test
    void ilLimiteDeveEsserePositivoELIdPresente() {
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("A1", ora.plusSeconds(60), 0, pos));
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("A1", ora.plusSeconds(60), -50, pos));
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("  ", ora.plusSeconds(60), 70, pos));
        assertEquals(1, esterno.read("A1").size());
    }

    @Test
    void laStoriaLettaNonSiPuoModificare() {
        assertThrows(UnsupportedOperationException.class, () -> gestione.history("A1").clear());
        assertEquals(1, gestione.history("A1").size());
    }

    @Test
    void ilRefreshManualeVedeUnCambioFattoFuoriDalSistema() {
        assertEquals(50, allIstante(ora).get().getLimit());
        esterno.load("A1", ora.toString(), "80", "43.77", "11.25", "Via Roma 1");
        assertEquals(50, allIstante(ora).get().getLimit());                  // la cache non lo sa ancora
        gestione.refresh();
        assertEquals(80, allIstante(ora).get().getLimit());
    }

    @Test
    void unaMisuraInRitardoSiGiudicaConIlLimiteDiAlloraAncheDopoUnCambioProgrammato() {
        List<Detection> viste = new ArrayList<>();
        SpeedCheck controllo = new SpeedCheck(cache, r -> { viste.add(r); return UUID.randomUUID(); });
        Instant cambio = ora.plus(Duration.ofHours(3));
        gestione.schedule("A1", cambio, 70, new Location("Via Verdi 2", 45.0, 9.0));

        assertTrue(controllo.evaluate(new Measurement("A1", "AB123CD", 60, ora.plus(Duration.ofHours(2)))).isPresent());   // limite 50
        assertFalse(controllo.evaluate(new Measurement("A1", "AB123CD", 60, cambio)).isPresent());                         // limite 70

        clock.advance(Duration.ofHours(5));                                                                       // il cambio è passato
        assertTrue(controllo.evaluate(new Measurement("A1", "AB123CD", 60, ora.plus(Duration.ofHours(2)))).isPresent());   // misura in ritardo: ancora 50
        assertEquals(50, viste.get(0).getLimit());
        assertEquals(50, viste.get(1).getLimit());
        assertEquals("Via Roma 1", viste.get(1).getLocation().get().getAddress());
    }
}
