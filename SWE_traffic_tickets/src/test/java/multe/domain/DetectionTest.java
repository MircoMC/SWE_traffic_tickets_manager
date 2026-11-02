package fines.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DetectionTest {

    private static Detection at(int speed, int limit) {
        return new Detection("A1", "AB123CD", speed, limit, Instant.now());
    }

    @Test
    void below100KmhTheMarginIsFiveKmh() {
        assertFalse(at(55, 50).isViolation());
        assertTrue(at(56, 50).isViolation());
    }

    @Test
    void from100KmhTheMarginIsFivePercent() {
        assertFalse(at(105, 100).isViolation());   // 105 * 0.95 = 99.75
        assertTrue(at(106, 100).isViolation());    // 106 * 0.95 = 100.7
    }

    @Test
    void excessIsComputedOnTheRawSpeed() {
        assertEquals(5, at(55, 50).getExcess());
        assertEquals(0, at(40, 50).getExcess());
    }

    @Test
    void plateIsNormalized() {
        assertEquals("AB123CD", new Detection("A1", " ab123cd ", 90, 50, Instant.now()).getPlate());
    }

    @Test
    void invalidDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> at(0, 50));
        assertThrows(IllegalArgumentException.class, () -> at(90, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new Detection(" ", "AB123CD", 90, 50, Instant.now()));
    }
}
