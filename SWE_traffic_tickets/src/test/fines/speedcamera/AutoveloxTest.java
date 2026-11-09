package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import fines.app.AdjustableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** L'autovelox è un sensore generico: misura e consegna, non sa nulla di limiti né di posizioni. */
class AutoveloxTest {

    private AdjustableClock clock;
    private List<Measurement> ricevute;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        ricevute = new ArrayList<>();
    }

    private SpeedCamera sensore(String id, Optional<UUID> risposta) {
        return new SpeedCamera(id, m -> { ricevute.add(m); return risposta; }, clock);
    }

    @Test
    void consegnaLaMisuraConIdTargaNormalizzataVelocitaEIstante() {
        SpeedCamera a = sensore("A7", Optional.empty());
        a.detect(" ab123cd ", 92);

        assertEquals(1, ricevute.size());
        Measurement m = ricevute.get(0);
        assertEquals("A7", m.getCameraId());
        assertEquals("AB123CD", m.getPlate());
        assertEquals(92, m.getSpeed());
        assertEquals(clock.instant(), m.getDetectedAt());
    }

    @Test
    void restituisceLEsitoDiChiGiudica() {
        UUID multa = UUID.randomUUID();
        assertEquals(Optional.of(multa), sensore("A1", Optional.of(multa)).detect("AB123CD", 92));
        assertEquals(Optional.empty(), sensore("A1", Optional.empty()).detect("AB123CD", 30));
    }

    @Test
    void laStessaClassePuoFareDaPiuAutovelox() {
        SpeedCamera a = sensore("A1", Optional.empty());
        SpeedCamera b = sensore("B2", Optional.empty());
        a.detect("AB123CD", 60);
        b.detect("AB123CD", 60);
        assertEquals("A1", ricevute.get(0).getCameraId());
        assertEquals("B2", ricevute.get(1).getCameraId());
    }

    @Test
    void datiNonValidiSonoRifiutati() {
        SpeedCamera a = sensore("A1", Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> a.detect("AB123CD", 0));
        assertThrows(IllegalArgumentException.class, () -> a.detect("  ", 80));
        assertThrows(IllegalArgumentException.class, () -> sensore(" ", Optional.empty()));
        assertTrue(ricevute.isEmpty());
    }
}
