package multe.storico;

import static multe.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import multe.dominio.Annullamento;
import multe.dominio.ServiziMulte;
import multe.login.AutenticazioneException;
import multe.login.DataBasePassword;
import multe.login.GatewayLogin;
import multe.login.Ruolo;
import multe.persistenza.MultaDAOInMemory;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.GatewayContatore;
import multe.utenti.Giudice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginEAnnullamentoTest {

    private ServiziMulte servizi;
    private LoginProxy login;

    @BeforeEach
    void setUp() {
        MultaDAOInMemory dao = new MultaDAOInMemory();
        RegistroTarghe registro = new RegistroTargheInMemory();
        registro.assegna("AB123CD", 1);
        servizi = new ServiziMulte(dao, registro, new GatewayContatore());

        DataBasePassword db = new DataBasePassword();
        db.registra("mario", "pwdMario1!".toCharArray(), 1, Ruolo.CITTADINO);
        db.registra("rossi", "pwdRossi3!".toCharArray(), 3, Ruolo.CARABINIERE);
        db.registra("admin", "pwdAdmin4!".toCharArray(), 4, Ruolo.AMMINISTRATORE);
        db.registra("giudice", "pwdGiudice5!".toCharArray(), 10, Ruolo.GIUDICE);
        login = new LoginProxy(new GatewayLogin(db), new StoricoMulteTotale(dao), servizi);
    }

    @Test
    void soloIlGiudiceOttieneLAnnullamento() throws Exception {
        assertFalse(login.accedi("mario", "pwdMario1!".toCharArray()).getAnnullamento().isPresent());
        assertFalse(login.accedi("rossi", "pwdRossi3!".toCharArray()).getAnnullamento().isPresent());
        assertFalse(login.accedi("admin", "pwdAdmin4!".toCharArray()).getAnnullamento().isPresent());
        assertTrue(login.accedi("giudice", "pwdGiudice5!".toCharArray()).getAnnullamento().isPresent());
    }

    @Test
    void ilGiudiceAnnullaDopoIlLoginELaTracciaHaIlSuoId() throws Exception {
        int id = crea(servizi, "AB123CD", 80);
        Annullamento a = login.accedi("giudice", "pwdGiudice5!".toCharArray()).getAnnullamento().get();
        a.annulla(id, "Ricorso accolto");

        assertEquals("ANNULLATA", servizi.getStato(id).getState());
        assertEquals(10, servizi.getCopia(id).getProvvedimento().get().getIdGiudice());
    }

    @Test
    void ilTipoDiUtenteVieneDalRuoloSalvatoNonDalChiamante() throws Exception {
        assertTrue(login.accedi("giudice", "pwdGiudice5!".toCharArray()).getUtente() instanceof Giudice);
        assertFalse(login.accedi("mario", "pwdMario1!".toCharArray()).getUtente() instanceof Giudice);
    }

    @Test
    void senzaServiziNessunoOttieneLAnnullamento() throws Exception {
        LoginProxy senza = new LoginProxy(login0(), new StoricoMulteTotale(new MultaDAOInMemory()));
        assertFalse(senza.accedi("giudice", "pwdGiudice5!".toCharArray()).getAnnullamento().isPresent());
    }

    private GatewayLogin login0() {
        DataBasePassword db = new DataBasePassword();
        db.registra("giudice", "pwdGiudice5!".toCharArray(), 10, Ruolo.GIUDICE);
        return new GatewayLogin(db);
    }

    @Test
    void credenzialiErrateEGenericheEPasswordAzzerata() {
        char[] sbagliata = "sbagliata".toCharArray();
        AutenticazioneException e1 = assertThrows(AutenticazioneException.class, () -> login.accedi("mario", sbagliata));
        AutenticazioneException e2 = assertThrows(AutenticazioneException.class,
            () -> login.accedi("inesistente", "qualsiasi".toCharArray()));
        assertEquals(e1.getMessage(), e2.getMessage());   // non rivela se manca l'utente o è sbagliata la password
        for (char c : sbagliata) {
            assertEquals('\0', c);
        }
    }
}
