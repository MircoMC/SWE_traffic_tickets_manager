package multe.dominio;

import static multe.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import multe.app.OrologioModificabile;
import multe.persistenza.MultaDAOInMemory;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.GatewayContatore;
import multe.utenti.Giudice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnullamentoTest {

    private MultaDAOInMemory dao;
    private OrologioModificabile clock;
    private ServiziMulte servizi;
    private Annullamento annullamento;

    @BeforeEach
    void setUp() {
        dao = new MultaDAOInMemory();
        clock = new OrologioModificabile();
        RegistroTarghe registro = new RegistroTargheInMemory(clock);
        registro.assegna("AB123CD", 1);
        servizi = new ServiziMulte(dao, registro, new GatewayContatore(), clock);
        annullamento = servizi.annullamentoPer(new Giudice(10, "giudice"));
    }

    @Test
    void ilProvvedimentoRegistraChiQuandoEPerche() {
        int id = crea(servizi, clock, "AB123CD", 80);
        annullamento.annulla(id, "  Errore di rilevazione  ");

        Provvedimento p = servizi.getCopia(id).getProvvedimento().get();
        assertEquals(10, p.getIdGiudice());
        assertEquals("Errore di rilevazione", p.getMotivazione());
        assertEquals(LocalDate.now(clock), p.getData());
        // il provvedimento arriva anche nel DAO
        assertEquals(10, dao.trovaPerId(id).get().getProvvedimento().get().getIdGiudice());
    }

    @Test
    void laMotivazioneEObbligatoria() {
        int id = crea(servizi, clock, "AB123CD", 80);
        assertThrows(IllegalArgumentException.class, () -> annullamento.annulla(id, null));
        assertThrows(IllegalArgumentException.class, () -> annullamento.annulla(id, "   "));
        assertEquals("EMESSA", servizi.getStato(id).getState());
    }

    @Test
    void multaInesistente() {
        assertThrows(IllegalArgumentException.class, () -> annullamento.annulla(42, "Ricorso"));
    }
}
