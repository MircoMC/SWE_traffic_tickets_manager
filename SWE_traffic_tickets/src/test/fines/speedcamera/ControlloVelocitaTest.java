package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import fines.app.AdjustableClock;
import fines.domain.Fine;
import fines.domain.Detection;
import fines.domain.FineService;
import fines.domain.UnregisteredPlateException;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Il controllo non contiene nessuna soglia: usa la configurazione del mapper valida all'istante della misura. */
class ControlloVelocitaTest {

    private InMemoryFineDAO dao;
    private AdjustableClock clock;
    private FineService servizi;
    private ExternalMapper esterno;
    private SpeedCheck controllo;
    private Instant t0;

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        clock = new AdjustableClock();
        t0 = clock.instant();
        PlateRegistry registro = new InMemoryPlateRegistry(clock);
        registro.assign("AB123CD", 1, t0.minus(Duration.ofDays(400)));
        servizi = new FineService(dao, registro, new GatewayContatore(), clock);
        esterno = new ExternalMapper(clock);
        esterno.load("A1", "2020-01-01T00:00:00Z", "50", "43.77", "11.25", "Via Roma 1");
        controllo = new SpeedCheck(new SpeedCameraMapAdapter(esterno), servizi::createFine);
    }

    private Optional<UUID> misura(String autovelox, int velocita, Instant istante) {
        return controllo.evaluate(new Measurement(autovelox, "AB123CD", velocita, istante));
    }

    @Test
    void sopraIlLimiteCreaLaMultaConLimiteEPosizioneFotografati() {
        UUID id = misura("A1", 92, t0).get();
        Fine m = servizi.getCopy(id);
        Detection r = m.getDetection();

        assertEquals(57_000L, m.getAmountCents());
        assertEquals("A1", r.getCameraId());
        assertEquals(50, r.getLimit());
        assertEquals("Via Roma 1", r.getLocation().get().getAddress());
        assertEquals(43.77, r.getLocation().get().getLatitude());
        assertEquals(11.25, r.getLocation().get().getLongitude());
    }

    @Test
    void entroLimiteEMargineDiMisuraNonSuccedeNulla() {
        assertFalse(misura("A1", 50, t0).isPresent());
        assertFalse(misura("A1", 55, t0).isPresent());     // +5: dentro il margine
        assertTrue(misura("A1", 56, t0).isPresent());
        assertEquals(1, dao.findByOwner(1).size());
    }

    @Test
    void ilLimiteDipendeDallAutoveloxNonDallaClasse() {
        esterno.load("A2", "2020-01-01T00:00:00Z", "90", "44.10", "12.20", "Strada Provinciale 5");
        assertTrue(misura("A1", 80, t0).isPresent());      // limite 50
        assertFalse(misura("A2", 80, t0).isPresent());     // limite 90, stessa classe
    }

    @Test
    void unCambioProgrammatoVaDalSuoIstanteEUnaMisuraInRitardoUsaIlLimiteDiAllora() {
        // dal giorno 10 il limite sale a 70 e l'autovelox (mobile) cambia posizione
        esterno.schedule("A1", t0.plus(Duration.ofDays(10)).toString(), "70", "45.00", "9.00", "Via Verdi 2");

        UUID prima = misura("A1", 60, t0.plus(Duration.ofDays(5))).get();     // limite 50: 60 è infrazione
        assertFalse(misura("A1", 60, t0.plus(Duration.ofDays(10))).isPresent());  // dal giorno 10: limite 70
        UUID dopo = misura("A1", 100, t0.plus(Duration.ofDays(12))).get();

        // la misura del giorno 5 arriva solo al giorno 20: si giudica con il limite del giorno 5
        clock.advance(Duration.ofDays(20));
        UUID tardiva = misura("A1", 60, t0.plus(Duration.ofDays(5))).get();

        assertEquals(50, servizi.getCopy(prima).getDetection().getLimit());
        assertEquals("Via Roma 1", servizi.getCopy(prima).getDetection().getLocation().get().getAddress());
        assertEquals(70, servizi.getCopy(dopo).getDetection().getLimit());
        assertEquals("Via Verdi 2", servizi.getCopy(dopo).getDetection().getLocation().get().getAddress());
        assertEquals(50, servizi.getCopy(tardiva).getDetection().getLimit());
    }

    @Test
    void senzaConfigurazioneNonSiIndovinaUnLimite() {
        assertThrows(ConfigurationUnavailableException.class, () -> misura("A9", 120, t0));    // sconosciuto
        esterno.load("B1", t0.toString(), "50", "43.0", "11.0", "Via Nuova");
        assertThrows(ConfigurationUnavailableException.class,
            () -> misura("B1", 120, t0.minus(Duration.ofDays(1))));                                // prima del primo periodo
        assertTrue(dao.findByOwner(1).isEmpty());
    }

    @Test
    void unaTargaNonRegistrataNonVieneNascosta() {
        assertThrows(UnregisteredPlateException.class,
            () -> controllo.evaluate(new Measurement("A1", "ZZ000ZZ", 120, t0)));
    }
}
