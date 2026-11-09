package fines.plates;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeSet;
import fines.app.AdjustableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistroTargheTest {

    private AdjustableClock clock;
    private PlateRegistry registro;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        registro = new InMemoryPlateRegistry(clock);
    }

    @Test
    void laTargaVieneNormalizzata() {
        registro.assign(" ab123cd ", 1);
        assertEquals(Optional.of(1), registro.currentOwner("AB123CD"));
        assertEquals(Optional.of(1), registro.currentOwner("ab123cd"));
    }

    @Test
    void laStoriaDeiProprietariDipendeDallIstante() {
        Instant t0 = clock.instant();
        registro.assign("AB123CD", 1, t0);
        registro.assign("AB123CD", 2, t0.plus(Duration.ofDays(10)));

        assertEquals(Optional.empty(), registro.ownerAt("AB123CD", t0.minusSeconds(1)));
        assertEquals(Optional.of(1), registro.ownerAt("AB123CD", t0));
        assertEquals(Optional.of(1), registro.ownerAt("AB123CD", t0.plus(Duration.ofDays(9))));
        assertEquals(Optional.of(2), registro.ownerAt("AB123CD", t0.plus(Duration.ofDays(10))));
        assertEquals(Optional.of(2), registro.ownerAt("AB123CD", t0.plus(Duration.ofDays(99))));
    }

    @Test
    void ilProprietarioAttualeSegueIlClock() {
        registro.assign("AB123CD", 1);
        clock.advance(Duration.ofDays(1));
        registro.assign("AB123CD", 2);
        assertEquals(Optional.of(2), registro.currentOwner("AB123CD"));
    }

    @Test
    void targheDiContaSoloQuelleAttuali() {
        registro.assign("AB123CD", 1);
        registro.assign("XY999ZZ", 1);
        registro.assign("EF456GH", 2);
        clock.advance(Duration.ofDays(1));
        registro.assign("AB123CD", 2);          // venduta

        assertEquals(new TreeSet<>(Arrays.asList("XY999ZZ")), registro.platesOf(1));
        assertEquals(new TreeSet<>(Arrays.asList("AB123CD", "EF456GH")), registro.platesOf(2));
    }

    @Test
    void targaSconosciutaOMancante() {
        assertEquals(Optional.empty(), registro.currentOwner("ZZ000ZZ"));
        assertThrows(IllegalArgumentException.class, () -> registro.assign("  ", 1));
        assertThrows(IllegalArgumentException.class, () -> registro.currentOwner(null));
    }
}
