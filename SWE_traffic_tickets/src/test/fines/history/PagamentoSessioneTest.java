package fines.history;

import static fines.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import fines.domain.Fine;
import fines.domain.FineService;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PagamentoSessioneTest {

    private InMemoryFineDAO dao;
    private GatewayContatore gateway;
    private FineService servizi;
    private LoginProxy login;
    private UUID multaDiMario;
    private UUID multaDiLuca;

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        gateway = new GatewayContatore();
        PlateRegistry registro = new InMemoryPlateRegistry();
        registro.assign("AB123CD", 1);
        registro.assign("EF456GH", 2);
        registro.assign("XY999ZZ", 10);
        servizi = new FineService(dao, registro, gateway);
        multaDiMario = crea(servizi, "AB123CD", 80);
        multaDiLuca = crea(servizi, "EF456GH", 80);

        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("giudice", "pwdGiudice5!".toCharArray(), 10);
        db.registerOffice("giudice", "G-001", "pwdCaricaGiudice!".toCharArray(), Role.JUDGE);
        login = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao), servizi);
    }

    @Test
    void ilCittadinoPagaUnaMultaScegliendolaDallaPropriaLista() throws Exception {
        CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
        Fine scelta = mario.getMyFines().get(0);
        assertEquals("ISSUED", scelta.getState().getState());

        assertTrue(mario.pay(scelta.getId()));

        assertEquals("PAID", servizi.getState(scelta.getId()).getState());
        assertEquals("PAID", dao.findById(scelta.getId()).get().getState().getState());
        assertEquals("PAID", mario.getMyFines().get(0).getState().getState());   // la lista va riletta: le copie sono fotografie
        assertEquals(1, gateway.addebiti());
    }

    @Test
    void unaMultaGiaPagataNonSiPagaDueVolte() throws Exception {
        CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
        mario.pay(multaDiMario);
        assertThrows(IllegalStateException.class, () -> mario.pay(multaDiMario));
        assertEquals(1, gateway.addebiti());
    }

    @Test
    void ilGestoreCheRifiutaLasciaLaMultaEmessa() throws Exception {
        CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
        gateway.rifiuta(true);
        assertFalse(mario.pay(multaDiMario));
        assertEquals("ISSUED", servizi.getState(multaDiMario).getState());
    }

    @Test
    void multaInesistente() throws Exception {
        CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
        assertThrows(IllegalArgumentException.class, () -> mario.pay(UUID.randomUUID()));
    }

    @Test
    void senzaServiziLaSessioneNonPuoPagare() throws Exception {
        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        LoginProxy senza = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao));
        CitizenSession mario = senza.openSession("mario", "pwdMario1!".toCharArray());
        assertThrows(IllegalStateException.class, () -> mario.pay(multaDiMario));
        assertEquals("ISSUED", servizi.getState(multaDiMario).getState());
    }

    /** SCELTA DI PROGETTO dichiarata in OnlinePayment: chi ha fatto il login può pagare qualsiasi multa per id. */
    @Test
    void chiHaFattoIlLoginPuoPagareAncheUnaMultaAltrui() throws Exception {
        CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
        assertTrue(mario.pay(multaDiLuca));
        assertEquals("PAID", servizi.getState(multaDiLuca).getState());
    }

    @Test
    void ilGiudiceCheEntraDaCittadinoPagaLeProprieMulteComeTutti() throws Exception {
        UUID sua = crea(servizi, "XY999ZZ", 80);                 // la multa intestata al giudice stesso
        CitizenSession giudice = login.openSession("giudice", "pwdGiudice5!".toCharArray());
        assertEquals(1, giudice.getMyFines().size());
        assertTrue(giudice.pay(sua));
        assertEquals("PAID", servizi.getState(sua).getState());
    }
}
