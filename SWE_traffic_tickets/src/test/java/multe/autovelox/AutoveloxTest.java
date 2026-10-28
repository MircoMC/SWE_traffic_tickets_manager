package multe.autovelox;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalInt;
import multe.app.OrologioModificabile;
import multe.dominio.Multa;
import multe.dominio.ServiziMulte;
import multe.dominio.TargaNonRegistrataException;
import multe.persistenza.MultaDAOInMemory;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.GatewayContatore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoveloxTest {

    private MultaDAOInMemory dao;
    private OrologioModificabile clock;
    private ServiziMulte servizi;
    private Autovelox autovelox;

    @BeforeEach
    void setUp() {
        dao = new MultaDAOInMemory();
        clock = new OrologioModificabile();
        RegistroTarghe registro = new RegistroTargheInMemory(clock);
        registro.assegna("AB123CD", 1);
        servizi = new ServiziMulte(dao, registro, new GatewayContatore(), clock);
        autovelox = new Autovelox("A7", 50, new Location("Via Roma 1", 43.77, 11.25), servizi::creaMulta, clock);
    }

    @Test
    void sopraIlLimiteCreaUnaMultaConTuttiIDatiDellaMisura() {
        OptionalInt id = autovelox.rileva("ab123cd", 92);
        assertTrue(id.isPresent());

        Multa m = servizi.getCopia(id.getAsInt());
        assertEquals("AB123CD", m.getPlate());
        assertEquals(57_000L, m.getAmountCents());
        assertEquals(1, m.getIdIntestatario());
        assertEquals("A7", m.getRilevazione().getIdAutovelox());
        assertEquals(50, m.getRilevazione().getLimite());
        assertEquals(clock.instant(), m.getRilevazione().getRilevataIl());
    }

    @Test
    void entroIlLimiteNonSuccedeNulla() {
        assertFalse(autovelox.rileva("AB123CD", 50).isPresent());
        assertFalse(autovelox.rileva("AB123CD", 30).isPresent());
        assertTrue(dao.trovaPerIntestatario(1).isEmpty());
    }

    @Test
    void ilMargineDiMisuraEvitaLeMulteAlLimite() {
        assertFalse(autovelox.rileva("AB123CD", 55).isPresent());   // +5 km/h con limite 50: nessuna multa
        assertTrue(autovelox.rileva("AB123CD", 56).isPresent());    // +6: multa
        assertEquals(6_000L + 15_000L, servizi.getCopia(1).getAmountCents());   // l'importo usa l'eccesso sulla velocità rilevata

        autovelox.setLimite(140);
        assertFalse(autovelox.rileva("AB123CD", 142).isPresent());  // 142 * 95% = 134,9
        assertTrue(autovelox.rileva("AB123CD", 148).isPresent());   // 148 * 95% = 140,6 > 140
    }

    @Test
    void cambiareIlLimiteVaLeSoloPerLeMulteFuture() {
        int vecchia = autovelox.rileva("AB123CD", 92).getAsInt();
        autovelox.setLimite(70);
        int nuova = autovelox.rileva("AB123CD", 92).getAsInt();

        assertEquals(57_000L, servizi.getCopia(vecchia).getAmountCents());     // limite 50 fotografato nella rilevazione
        assertEquals(50, servizi.getCopia(vecchia).getRilevazione().getLimite());
        assertEquals(37_000L, servizi.getCopia(nuova).getAmountCents());       // limite 70
        assertFalse(autovelox.rileva("AB123CD", 65).isPresent());              // ora 65 è lecito
    }

    @Test
    void ilLimiteDeveEsserePositivo() {
        assertThrows(IllegalArgumentException.class, () -> autovelox.setLimite(0));
        assertThrows(IllegalArgumentException.class, () -> autovelox.setLimite(-10));
        assertEquals(50, autovelox.getLimite());
        assertThrows(IllegalArgumentException.class,
            () -> new Autovelox("A8", 0, new Location("x", 0, 0), servizi::creaMulta, clock));
    }

    @Test
    void unaTargaNonRegistrataNonCreaMulte() {
        assertThrows(TargaNonRegistrataException.class, () -> autovelox.rileva("ZZ000ZZ", 90));
    }
}
