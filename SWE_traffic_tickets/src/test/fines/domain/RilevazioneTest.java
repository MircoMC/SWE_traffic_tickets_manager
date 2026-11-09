package fines.domain;

import fines.speedcamera.Location;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RilevazioneTest {

    private static final Instant T = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void normalizzaLaTargaECalcolaLEccesso() {
        Detection r = new Detection("A1", " ab123cd ", 92, 70, T);
        assertEquals("AB123CD", r.getPlate());
        assertEquals(22, r.getExcess());
        assertTrue(r.isViolation());
    }

    @Test
    void alLimiteONonSiEInfrazione() {
        assertFalse(new Detection("A1", "AB123CD", 70, 70, T).isViolation());
        assertEquals(0, new Detection("A1", "AB123CD", 40, 70, T).getExcess());
    }

    @Test
    void valoriNonValidiSonoRifiutati() {
        assertThrows(IllegalArgumentException.class, () -> new Detection(" ", "AB123CD", 80, 50, T));
        assertThrows(IllegalArgumentException.class, () -> new Detection("A1", "  ", 80, 50, T));
        assertThrows(IllegalArgumentException.class, () -> new Detection("A1", "AB123CD", 0, 50, T));
        assertThrows(IllegalArgumentException.class, () -> new Detection("A1", "AB123CD", 80, 0, T));
        assertThrows(NullPointerException.class, () -> new Detection("A1", "AB123CD", 80, 50, null));
    }

    @Test
    void dueRilevazioniConGliStessiDatiSonoUguali() {
        assertEquals(new Detection("A1", "ab123cd", 80, 50, T), new Detection("A1", "AB123CD", 80, 50, T));
    }

    private static boolean infrazione(int velocita, int limite) {
        return new Detection("A1", "AB123CD", velocita, limite, T).isViolation();
    }

    @Test
    void sotto100KmhServonoPiuDi5KmhOltreIlLimite() {
        assertFalse(infrazione(50, 50));
        assertFalse(infrazione(54, 50));
        assertFalse(infrazione(55, 50));    // esattamente +5: dentro il margine di misura
        assertTrue(infrazione(56, 50));
        assertFalse(infrazione(95, 90));
        assertTrue(infrazione(96, 90));
        assertTrue(infrazione(99, 93));
    }

    @Test
    void da100KmhInPoiServeIlSuperamentoDelLimiteAlNettoDelCinquePerCento() {
        assertFalse(infrazione(105, 100));  // 105 * 95% = 99,75
        assertTrue(infrazione(106, 100));   // 106 * 95% = 100,7
        assertFalse(infrazione(115, 110));  // 109,25
        assertTrue(infrazione(116, 110));   // 110,2
        assertTrue(infrazione(150, 140));   // 142,5 > 140
        assertFalse(infrazione(157, 150));  // 149,15
        assertTrue(infrazione(158, 150));   // 150,1
    }

    @Test
    void aCentoKmhIDueMarginiCoincidono() {
        assertTrue(infrazione(100, 94));
        assertFalse(infrazione(100, 95));
    }

    @Test
    void laPercentualeNonVieneTroncataAgliInteri() {
        // 123 * 95% = 116,85 > 116: infrazione. Con la divisione intera (123 * 95 / 100 = 116) non lo sarebbe.
        assertTrue(infrazione(123, 116));
        assertFalse(infrazione(123, 117));
    }

    @Test
    void eccessoPerLImportoUsaLaVelocitaRilevata() {
        Detection r = new Detection("A1", "AB123CD", 56, 50, T);
        assertTrue(r.isViolation());
        assertEquals(6, r.getExcess());
        assertFalse(infrazione(53, 50));
        assertEquals(3, new Detection("A1", "AB123CD", 53, 50, T).getExcess());   // positivo ma non infrazione
    }

    @Test
    void laPosizioneEOpzionaEFaParteDellUguaglianza() {
        Location roma = new Location("Via Roma 1", 43.77, 11.25);
        Detection senza = new Detection("A1", "AB123CD", 80, 50, T);
        Detection con = new Detection("A1", "AB123CD", 80, 50, T, roma);

        assertFalse(senza.getLocation().isPresent());
        assertEquals(roma, con.getLocation().get());
        assertFalse(senza.equals(con));
        assertEquals(con, new Detection("A1", "AB123CD", 80, 50, T, new Location("Via Roma 1", 43.77, 11.25)));
    }
}
