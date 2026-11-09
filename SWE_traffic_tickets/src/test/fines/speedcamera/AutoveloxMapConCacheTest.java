package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import fines.app.AdjustableClock;
import fines.testutil.MapCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoveloxMapConCacheTest {

    private AdjustableClock clock;
    private ExternalMapper esterno;
    private MapCounter sorgente;
    private CachedSpeedCameraMap cache;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        esterno = new ExternalMapper(clock);
        esterno.load("A1", "2020-01-01T00:00:00Z", "50", "43.77", "11.25", "Via Roma 1");
        sorgente = new MapCounter(new SpeedCameraMapAdapter(esterno));
        cache = new CachedSpeedCameraMap(sorgente, clock);
    }

    private int limiteAdesso(String id) {
        return Period.configurationAt(cache.history(id), clock.instant()).get().getLimit();
    }

    @Test
    void laSorgenteSiLeggeUnaVoltaAlGiorno() {
        for (int i = 0; i < 5; i++) {
            assertEquals(50, limiteAdesso("A1"));
        }
        assertEquals(1, sorgente.chiamate());

        clock.advance(Duration.ofHours(23));
        limiteAdesso("A1");
        assertEquals(1, sorgente.chiamate());

        clock.advance(Duration.ofHours(2));      // 25 ore dopo la lettura
        limiteAdesso("A1");
        assertEquals(2, sorgente.chiamate());
    }

    @Test
    void unCambioImmediatoSiVedeSoloDopoIlRefresh() {
        assertEquals(50, limiteAdesso("A1"));
        // un cambio fatto FUORI dal nostro sistema (quello fatto da noi invalida la cache da solo)
        esterno.load("A1", clock.instant().toString(), "70", "43.77", "11.25", "Via Roma 1");

        // scelta di progetto: la cache può avere fino a un giorno
        assertEquals(50, limiteAdesso("A1"));

        cache.refresh();                        // refresh a mano
        assertEquals(70, limiteAdesso("A1"));
    }

    @Test
    void unCambioProgrammatoScattaAllOraGiustaSenzaNessunRefresh() {
        Instant tra3Ore = clock.instant().plus(Duration.ofHours(3));
        esterno.schedule("A1", tra3Ore.toString(), "70", "43.77", "11.25", "Via Roma 1");

        assertEquals(50, limiteAdesso("A1"));
        clock.advance(Duration.ofHours(4));
        assertEquals(70, limiteAdesso("A1"));    // già nella history in cache: nessuna nuova lettura
        assertEquals(1, sorgente.chiamate());
    }

    @Test
    void ilRefreshDiUnAutoveloxNonToccaGliAltri() {
        esterno.load("A2", "2020-01-01T00:00:00Z", "90", "44.10", "12.20", "SP 5");
        limiteAdesso("A1");
        limiteAdesso("A2");
        assertEquals(2, sorgente.chiamate());

        cache.refresh("A1");
        limiteAdesso("A1");
        limiteAdesso("A2");
        assertEquals(3, sorgente.chiamate());    // solo A1 riletto
    }

    @Test
    void seLaSorgenteNonRispondeSiUsaLUltimaCopiaFinoAlLimiteDiEta() {
        assertEquals(50, limiteAdesso("A1"));
        sorgente.nonRaggiungibile(true);

        clock.advance(Duration.ofHours(25));      // scaduta, ma la copia ha meno di 3 giorni
        assertEquals(50, limiteAdesso("A1"));

        clock.advance(Duration.ofDays(3));        // ora la copia è troppo vecchia
        assertThrows(IllegalStateException.class, () -> cache.history("A1"));
    }

    @Test
    void seLaSorgenteNonRispondeEMancaLaCopiaLErroreArriva() {
        sorgente.nonRaggiungibile(true);
        assertThrows(IllegalStateException.class, () -> cache.history("A1"));
    }

    @Test
    void unAutoveloxSconosciutoNonVieneMemorizzato() {
        assertTrue(cache.history("A9").isEmpty());
        esterno.load("A9", "2020-01-01T00:00:00Z", "30", "43.0", "11.0", "Via Nuova");
        assertEquals(30, limiteAdesso("A9"));    // si vede subito, senza aspettare un giorno
    }

    @Test
    void ilRefreshManualeTornaAChiedereTuttoAllaSorgente() {
        limiteAdesso("A1");
        cache.refresh();
        limiteAdesso("A1");
        assertEquals(2, sorgente.chiamate());
    }

    @Test
    void unCambioProgrammatoDaNoiSiVedeSubitoSenzaRefresh() {
        assertEquals(50, limiteAdesso("A1"));                        // la copia è in cache
        Instant tra1Ora = clock.instant().plus(Duration.ofHours(1));
        cache.schedule("A1", tra1Ora, new Configuration(70, new fines.speedcamera.Location("Via Roma 1", 43.77, 11.25)));

        assertEquals(2, cache.history("A1").size());                  // già visibile: la scrittura ha invalidato la cache
        assertEquals(2, sorgente.chiamate());
        clock.advance(Duration.ofHours(2));
        assertEquals(70, limiteAdesso("A1"));
    }

    @Test
    void laScritturaInvalidaSoloLAutoveloxInteressato() {
        esterno.load("A2", "2020-01-01T00:00:00Z", "90", "44.10", "12.20", "SP 5");
        limiteAdesso("A1");
        limiteAdesso("A2");
        assertEquals(2, sorgente.chiamate());

        cache.schedule("A1", clock.instant().plus(Duration.ofHours(1)),
            new Configuration(70, new fines.speedcamera.Location("Via Roma 1", 43.77, 11.25)));
        limiteAdesso("A1");
        limiteAdesso("A2");
        assertEquals(3, sorgente.chiamate());                        // solo A1 riletto (A2 era in cache)
    }

    @Test
    void seLaScritturaFallisceLaCacheRestaComEra() {
        limiteAdesso("A1");
        assertThrows(IllegalArgumentException.class, () -> cache.schedule("A1", clock.instant().minus(Duration.ofDays(1)),
            new Configuration(200, new fines.speedcamera.Location("x", 0, 0))));
        assertEquals(1, sorgente.chiamate());
        assertEquals(50, limiteAdesso("A1"));
        assertEquals(1, sorgente.chiamate());                        // nessuna invalidazione inutile
    }
}
