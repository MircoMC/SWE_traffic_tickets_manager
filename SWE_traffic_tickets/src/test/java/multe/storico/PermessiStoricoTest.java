package multe.storico;

import static multe.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import multe.dominio.Multa;
import multe.dominio.ServiziMulte;
import multe.login.DataBasePassword;
import multe.login.GatewayLogin;
import multe.login.Ruolo;
import multe.persistenza.MultaDAOInMemory;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.GatewayContatore;
import multe.utenti.Cittadino;
import multe.utenti.Utente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermessiStoricoTest {

    private LoginProxy login;
    private final Utente mario = new Cittadino(1, "mario");
    private final Utente luca = new Cittadino(2, "luca");

    @BeforeEach
    void setUp() {
        MultaDAOInMemory dao = new MultaDAOInMemory();
        RegistroTarghe registro = new RegistroTargheInMemory();
        registro.assegna("AB123CD", 1);
        registro.assegna("EF456GH", 2);
        ServiziMulte servizi = new ServiziMulte(dao, registro, new GatewayContatore());
        crea(servizi, "AB123CD", 80);
        crea(servizi, "EF456GH", 81);
        crea(servizi, "EF456GH", 82);

        DataBasePassword db = new DataBasePassword();
        db.registra("mario", "pwdMario1!".toCharArray(), 1, Ruolo.CITTADINO);
        db.registra("rossi", "pwdRossi3!".toCharArray(), 3, Ruolo.CARABINIERE);
        db.registra("giudice", "pwdGiudice5!".toCharArray(), 10, Ruolo.GIUDICE);
        login = new LoginProxy(new GatewayLogin(db), new StoricoMulteTotale(dao), servizi);
    }

    @Test
    void ilCittadinoVedeSoloLeProprieMulte() throws Exception {
        StoricoMulte s = login.login("mario", "pwdMario1!".toCharArray());
        assertEquals(1, s.getMulte(mario.getId()).size());
        assertThrows(SecurityException.class, () -> s.getMulte(luca.getId()));
    }

    @Test
    void ilCarabiniereVedeLeMulteDiTutti() throws Exception {
        StoricoMulte s = login.login("rossi", "pwdRossi3!".toCharArray());
        assertEquals(1, s.getMulte(mario.getId()).size());
        assertEquals(2, s.getMulte(luca.getId()).size());
    }

    @Test
    void ilGiudiceVedeLeMulteDiTutti() throws Exception {
        StoricoMulte s = login.login("giudice", "pwdGiudice5!".toCharArray());
        assertEquals(1, s.getMulte(mario.getId()).size());
        assertEquals(2, s.getMulte(luca.getId()).size());
    }

    @Test
    void laSessioneDelCittadinoMostraLeSueMulteSenzaIndicareNessunId() throws Exception {
        Sessione sessione = login.accedi("mario", "pwdMario1!".toCharArray());
        assertEquals(1, sessione.getMieMulte().size());
        assertEquals(1, sessione.getMieMulte().get(0).getIdIntestatario());
    }

    @Test
    void ilCittadinoNonPuoUsareLIdDiUnAltroNeConNumeriStraniNeNegativi() throws Exception {
        StoricoMulte s = login.login("mario", "pwdMario1!".toCharArray());
        for (int id : new int[] { 2, 3, 10, 0, -1, Integer.MAX_VALUE }) {
            int daVedere = id;
            assertThrows(SecurityException.class, () -> s.getMulte(daVedere));
        }
    }

    @Test
    void laListaRestituitaNonSiPuoModificare() throws Exception {
        StoricoMulte s = login.login("mario", "pwdMario1!".toCharArray());
        List<Multa> multe = s.getMulte(mario.getId());
        assertThrows(UnsupportedOperationException.class, multe::clear);
    }
}
