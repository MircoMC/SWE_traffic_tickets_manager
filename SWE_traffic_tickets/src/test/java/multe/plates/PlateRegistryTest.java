package fines.plates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlateRegistryTest {

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    private PlateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryPlateRegistry(Clock.fixed(t0.plus(Duration.ofDays(20)), ZoneOffset.UTC));
        registry.assign("AB123CD", 1, t0);
        registry.assign("AB123CD", 2, t0.plus(Duration.ofDays(10)));   // ownership transfer
    }

    @Test
    void ownerDependsOnTheInstant() {
        assertEquals(Optional.empty(), registry.ownerAt("AB123CD", t0.minus(Duration.ofDays(1))));
        assertEquals(Optional.of(1), registry.ownerAt("AB123CD", t0.plus(Duration.ofDays(5))));
        assertEquals(Optional.of(2), registry.ownerAt("AB123CD", t0.plus(Duration.ofDays(15))));
    }

    @Test
    void currentOwnerAndPlatesOf() {
        assertEquals(Optional.of(2), registry.currentOwner("AB123CD"));
        assertEquals(Collections.singleton("AB123CD"), registry.platesOf(2));
        assertTrue(registry.platesOf(1).isEmpty());
    }

    @Test
    void spellingVariantsAreTheSamePlate() {
        assertEquals(Optional.of(2), registry.currentOwner(" ab123cd "));
        assertEquals("AB123CD", PlateRegistry.normalize(" ab123cd "));
    }

    @Test
    void emptyPlateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlateRegistry.normalize("  "));
    }
}
