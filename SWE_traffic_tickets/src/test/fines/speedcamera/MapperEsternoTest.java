package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import fines.app.AdjustableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Le regole del servizio: la history è append-only verso il futuro e non si può avvelenare. */
class MapperEsternoTest {

    private AdjustableClock clock;
    private ExternalMapper esterno;
    private Instant ora;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        ora = clock.instant();
        esterno = new ExternalMapper(clock);
    }

    @Test
    void ilPassatoEIlPresenteNonSiProgrammano() {
        assertThrows(IllegalArgumentException.class,
            () -> esterno.schedule("A1", ora.minus(Duration.ofSeconds(1)).toString(), "70", "43.0", "11.0", "x"));
        assertThrows(IllegalArgumentException.class,
            () -> esterno.schedule("A1", ora.toString(), "70", "43.0", "11.0", "x"));
        assertTrue(esterno.read("A1").isEmpty());
    }

    @Test
    void ilFuturoSiProgramma() {
        esterno.schedule("A1", ora.plusSeconds(1).toString(), "70", "43.0", "11.0", "x");
        assertEquals(1, esterno.read("A1").size());
        assertEquals("70", esterno.read("A1").get(0).get("limite_kmh"));
    }

    @Test
    void ilTempoCheScorreRendePassatoUnInizioCheEraFuturo() {
        Instant tra1Ora = ora.plus(Duration.ofHours(1));
        clock.advance(Duration.ofHours(2));
        assertThrows(IllegalArgumentException.class,
            () -> esterno.schedule("A1", tra1Ora.toString(), "70", "43.0", "11.0", "x"));
    }

    @Test
    void unInizioGiaProgrammatoNonSiSovrascriveNemmenoSeScrittoInUnAltroFormato() {
        esterno.schedule("A1", "2099-01-01T00:00:00Z", "70", "43.0", "11.0", "x");
        assertThrows(IllegalStateException.class,
            () -> esterno.schedule("A1", "2099-01-01T00:00:00Z", "130", "43.0", "11.0", "x"));
        assertThrows(IllegalStateException.class,
            () -> esterno.schedule("A1", "2099-01-01T00:00:00.000Z", "130", "43.0", "11.0", "x"));
        assertEquals(1, esterno.read("A1").size());
        assertEquals("70", esterno.read("A1").get(0).get("limite_kmh"));
    }

    @Test
    void unInizioNonValidoNonEntraNellaStoria() {
        assertThrows(IllegalArgumentException.class, () -> esterno.schedule("A1", "non-una-data", "70", "43.0", "11.0", "x"));
        assertThrows(IllegalArgumentException.class, () -> esterno.schedule("A1", null, "70", "43.0", "11.0", "x"));
        assertTrue(esterno.read("A1").isEmpty());        // niente righe avvelenate
        assertTrue(new SpeedCameraMapAdapter(esterno).history("A1").isEmpty());
    }

    @Test
    void ilCaricamentoInizialeEIlCanaleDeiDatiDiPartenzaESoloQuello() {
        esterno.load("A1", "2020-01-01T00:00:00Z", "50", "43.0", "11.0", "x");     // nel passato: ammesso solo qui
        assertEquals(1, esterno.read("A1").size());
    }

    @Test
    void leRigheLetteNonSiModificanoDaFuori() {
        esterno.schedule("A1", "2099-01-01T00:00:00Z", "70", "43.0", "11.0", "x");
        assertThrows(UnsupportedOperationException.class, () -> esterno.read("A1").get(0).put("limite_kmh", "999"));
        assertEquals("70", esterno.read("A1").get(0).get("limite_kmh"));
    }
}
