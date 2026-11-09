package fines.domain;

import java.util.UUID;
import static fines.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import fines.app.AdjustableClock;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import fines.users.Judge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnullamentoTest {

    private InMemoryFineDAO dao;
    private AdjustableClock clock;
    private FineService servizi;
    private Cancellation annullamento;

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        clock = new AdjustableClock();
        PlateRegistry registro = new InMemoryPlateRegistry(clock);
        registro.assign("AB123CD", 1);
        registro.assign("EF456GH", 10);     // l'auto del giudice stesso
        servizi = new FineService(dao, registro, new GatewayContatore(), clock);
        annullamento = servizi.cancellationFor(new Judge(10, "giudice"));
    }

    @Test
    void ilProvvedimentoRegistraChiQuandoEPerche() {
        UUID id = crea(servizi, clock, "AB123CD", 80);
        annullamento.cancel(id, "  Errore di rilevazione  ");

        CancellationRuling p = servizi.getCopy(id).getRuling().get();
        assertEquals(10, p.getJudgeId());
        assertEquals("Errore di rilevazione", p.getReason());
        assertEquals(LocalDate.now(clock), p.getDate());
        // il provvedimento arriva anche nel DAO
        assertEquals(10, dao.findById(id).get().getRuling().get().getJudgeId());
    }

    @Test
    void laMotivazioneEObbligatoria() {
        UUID id = crea(servizi, clock, "AB123CD", 80);
        assertThrows(IllegalArgumentException.class, () -> annullamento.cancel(id, null));
        assertThrows(IllegalArgumentException.class, () -> annullamento.cancel(id, "   "));
        assertEquals("ISSUED", servizi.getState(id).getState());
    }

    @Test
    void multaInesistente() {
        assertThrows(IllegalArgumentException.class, () -> annullamento.cancel(UUID.randomUUID(), "Ricorso"));
    }

    @Test
    void ilGiudiceNonPuoAnnullareLeMulteIntestateASeStesso() {
        UUID sua = crea(servizi, clock, "EF456GH", 80);
        SecurityException e = assertThrows(SecurityException.class, () -> annullamento.cancel(sua, "Ricorso"));
        assertEquals("Il giudice non può annullare una multa a lui intestata", e.getMessage());
        assertEquals("ISSUED", servizi.getState(sua).getState());
        assertEquals("ISSUED", dao.findById(sua).get().getState().getState());
        assertFalse(servizi.getCopy(sua).getRuling().isPresent());
    }

    @Test
    void ilGiudicePuoAnnullareLeMulteDegliAltri() {
        UUID altrui = crea(servizi, clock, "AB123CD", 80);
        annullamento.cancel(altrui, "Ricorso accolto");
        assertEquals("CANCELLED", servizi.getState(altrui).getState());
    }
}
