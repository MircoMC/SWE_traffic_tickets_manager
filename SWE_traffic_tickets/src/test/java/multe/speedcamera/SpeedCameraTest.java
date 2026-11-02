package fines.speedcamera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fines.domain.Detection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpeedCameraTest {

    private List<Detection> delivered;
    private SpeedCamera camera;

    @BeforeEach
    void setUp() {
        delivered = new ArrayList<>();
        camera = new SpeedCamera("A1", 50, new Location("Via Roma 1", 43.77, 11.25),
            d -> { delivered.add(d); return delivered.size(); }, Clock.systemUTC());
    }

    @Test
    void withinTheMarginNothingIsDelivered() {
        assertFalse(camera.detect("AB123CD", 55).isPresent());
        assertTrue(delivered.isEmpty());
    }

    @Test
    void aViolationIsDeliveredAndTheIdIsReturned() {
        assertEquals(1, camera.detect("AB123CD", 90).getAsInt());
        assertEquals("AB123CD", delivered.get(0).getPlate());
    }

    @Test
    void changingTheLimitDoesNotAffectDetectionsAlreadyMade() {
        camera.detect("AB123CD", 90);
        camera.setLimit(80);
        assertEquals(50, delivered.get(0).getLimit());
        camera.detect("AB123CD", 90);
        assertEquals(80, delivered.get(1).getLimit());
    }

    @Test
    void invalidLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> camera.setLimit(0));
    }

    @Test
    void locationIsAValueObject() {
        assertEquals(new Location("Via Roma 1", 43.77, 11.25), camera.getLocation());
    }
}
