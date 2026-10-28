package multe.targhe;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeSet;
import multe.app.OrologioModificabile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistroTargheTest {

    private OrologioModificabile clock;
    private RegistroTarghe registro;

    @BeforeEach
    void setUp() {
        clock = new OrologioModificabile();
        registro = new RegistroTargheInMemory(clock);
    }

    @Test
    void laTargaVieneNormalizzata() {
        registro.assegna(" ab123cd ", 1);
        assertEquals(Optional.of(1), registro.proprietarioAttuale("AB123CD"));
        assertEquals(Optional.of(1), registro.proprietarioAttuale("ab123cd"));
    }

    @Test
    void laStoriaDeiProprietariDipendeDallIstante() {
        Instant t0 = clock.instant();
        registro.assegna("AB123CD", 1, t0);
        registro.assegna("AB123CD", 2, t0.plus(Duration.ofDays(10)));

        assertEquals(Optional.empty(), registro.proprietarioAl("AB123CD", t0.minusSeconds(1)));
        assertEquals(Optional.of(1), registro.proprietarioAl("AB123CD", t0));
        assertEquals(Optional.of(1), registro.proprietarioAl("AB123CD", t0.plus(Duration.ofDays(9))));
        assertEquals(Optional.of(2), registro.proprietarioAl("AB123CD", t0.plus(Duration.ofDays(10))));
        assertEquals(Optional.of(2), registro.proprietarioAl("AB123CD", t0.plus(Duration.ofDays(99))));
    }

    @Test
    void ilProprietarioAttualeSegueIlClock() {
        registro.assegna("AB123CD", 1);
        clock.avanza(Duration.ofDays(1));
        registro.assegna("AB123CD", 2);
        assertEquals(Optional.of(2), registro.proprietarioAttuale("AB123CD"));
    }

    @Test
    void targheDiContaSoloQuelleAttuali() {
        registro.assegna("AB123CD", 1);
        registro.assegna("XY999ZZ", 1);
        registro.assegna("EF456GH", 2);
        clock.avanza(Duration.ofDays(1));
        registro.assegna("AB123CD", 2);          // venduta

        assertEquals(new TreeSet<>(Arrays.asList("XY999ZZ")), registro.targheDi(1));
        assertEquals(new TreeSet<>(Arrays.asList("AB123CD", "EF456GH")), registro.targheDi(2));
    }

    @Test
    void targaSconosciutaOMancante() {
        assertEquals(Optional.empty(), registro.proprietarioAttuale("ZZ000ZZ"));
        assertThrows(IllegalArgumentException.class, () -> registro.assegna("  ", 1));
        assertThrows(IllegalArgumentException.class, () -> registro.proprietarioAttuale(null));
    }
}
