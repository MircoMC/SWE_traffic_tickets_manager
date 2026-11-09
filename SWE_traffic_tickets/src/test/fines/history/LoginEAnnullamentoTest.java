package fines.history;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import fines.domain.Cancellation;
import fines.domain.FineService;
import fines.login.AuthenticationException;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import fines.testutil.Infrazioni;
import fines.users.Administrator;
import fines.users.PoliceOfficer;
import fines.users.Citizen;
import fines.users.Judge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La matrice di accesso: cittadino (cf + password) e carica (cf + password + codice identificativo). */
class LoginEAnnullamentoTest {

    private PasswordDatabase db;
    private FineService servizi;
    private LoginProxy login;

    @BeforeEach
    void setUp() {
        InMemoryFineDAO dao = new InMemoryFineDAO();
        PlateRegistry registro = new InMemoryPlateRegistry();
        registro.assign("AB123CD", 1);
        servizi = new FineService(dao, registro, new GatewayContatore());

        db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("rossi", "pwdRossi3!".toCharArray(), 3);
        db.register("admin", "pwdAdmin4!".toCharArray(), 4);
        db.register("giudice", "pwdGiudice5!".toCharArray(), 10);
        db.registerOffice("rossi", "C-300", "pwdCaricaRossi!".toCharArray(), Role.POLICE_OFFICER);
        db.registerOffice("admin", "A-001", "pwdCaricaAdmin!".toCharArray(), Role.ADMINISTRATOR);
        db.registerOffice("giudice", "G-001", "pwdCaricaGiudice!".toCharArray(), Role.JUDGE);
        login = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao), servizi);
    }

    @Test
    void soloIlGiudiceConLaCaricaOttieneLAnnullamento() throws Exception {
        assertTrue(login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getCancellation().isPresent());
        assertFalse(login.openOfficeSession("rossi", "C-300", "pwdCaricaRossi!".toCharArray()).getCancellation().isPresent());
        assertFalse(login.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray()).getCancellation().isPresent());
    }

    @Test
    void ilGiudiceAnnullaConLaCaricaELaTracciaHaIlSuoId() throws Exception {
        UUID id = Infrazioni.crea(servizi, "AB123CD", 80);
        Cancellation a = login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getCancellation().get();
        a.cancel(id, "Ricorso accolto");

        assertEquals("CANCELLED", servizi.getState(id).getState());
        assertEquals(10, servizi.getCopy(id).getRuling().get().getJudgeId());
    }

    @Test
    void ilTipoDiUtenteVieneDalRuoloSalvatoNonDalChiamante() throws Exception {
        assertTrue(login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getUser() instanceof Judge);
        assertTrue(login.openOfficeSession("rossi", "C-300", "pwdCaricaRossi!".toCharArray()).getUser() instanceof PoliceOfficer);
        assertTrue(login.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray()).getUser() instanceof Administrator);
        // entrando da cittadino anche chi ha una carica è un semplice cittadino
        assertTrue(login.openSession("giudice", "pwdGiudice5!".toCharArray()).getUser() instanceof Citizen);
        assertTrue(login.openSession("rossi", "pwdRossi3!".toCharArray()).getUser() instanceof Citizen);
    }

    @Test
    void personaEAccountDiCaricaHannoLoStessoId() throws Exception {
        assertEquals(10, login.openSession("giudice", "pwdGiudice5!".toCharArray()).getUser().getId());
        assertEquals(10, login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getUser().getId());
    }

    @Test
    void lePasswordUgualiSonoAmmesse() throws Exception {
        db.register("verdi", "stessaPassword1!".toCharArray(), 20);
        db.registerOffice("verdi", "C-777", "stessaPassword1!".toCharArray(), Role.POLICE_OFFICER);
        assertNotNull(login.openSession("verdi", "stessaPassword1!".toCharArray()));
        assertNotNull(login.openOfficeSession("verdi", "C-777", "stessaPassword1!".toCharArray()));
    }

    @Test
    void unaPersonaPuoAvereDiUnaCarica() throws Exception {
        db.registerOffice("giudice", "A-555", "pwdAltraCarica!".toCharArray(), Role.ADMINISTRATOR);
        assertTrue(login.openOfficeSession("giudice", "A-555", "pwdAltraCarica!".toCharArray()).getUser() instanceof Administrator);
        assertTrue(login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getUser() instanceof Judge);
    }

    @Test
    void ogniCombinazioneSbagliataDaLoStessoErroreGenerico() {
        String[] messaggi = {
            erroreCittadino("mario", "sbagliata"),                    // password errata
            erroreCittadino("inesistente", "qualsiasi"),              // utente inesistente
            erroreCittadino("giudice", "pwdCaricaGiudice!"),          // la password della carica non apre l'account privato
            erroreCarica("giudice", "G-001", "pwdGiudice5!"),         // e quella privata non apre la carica
            erroreCarica("giudice", "G-001", "sbagliata"),            // password errata
            erroreCarica("giudice", "G-999", "pwdCaricaGiudice!"),    // codice identificativo errato
            erroreCarica("mario", "G-001", "pwdMario1!"),             // mario non ha quella carica
            erroreCarica("rossi", "G-001", "pwdCaricaGiudice!"),      // codice di un altro, password di un altro
            erroreCarica("inesistente", "X-1", "qualsiasi"),
        };
        for (String m : messaggi) {
            assertEquals("Credenziali non valide", m);
        }
    }

    private String erroreCittadino(String cf, String password) {
        return assertThrows(AuthenticationException.class, () -> login.openSession(cf, password.toCharArray())).getMessage();
    }

    private String erroreCarica(String cf, String codice, String password) {
        return assertThrows(AuthenticationException.class, () -> login.openOfficeSession(cf, codice, password.toCharArray())).getMessage();
    }

    @Test
    void lePasswordVengonoAzzerateAnchePerGliAccessiRifiutati() {
        char[] a = "sbagliata".toCharArray();
        char[] b = "sbagliata".toCharArray();
        assertThrows(AuthenticationException.class, () -> login.openSession("mario", a));
        assertThrows(AuthenticationException.class, () -> login.openOfficeSession("giudice", "G-001", b));
        for (char c : a) assertEquals('\0', c);
        for (char c : b) assertEquals('\0', c);
    }

    @Test
    void registrazioniNonValideSonoRifiutate() {
        // carica senza account privato
        assertThrows(IllegalStateException.class,
            () -> db.registerOffice("sconosciuto", "G-2", "pwd".toCharArray(), Role.JUDGE));
        // il cittadino non è una carica
        assertThrows(IllegalArgumentException.class,
            () -> db.registerOffice("mario", "X-1", "pwd".toCharArray(), Role.CITIZEN));
        // codice identificativo vuoto
        assertThrows(IllegalArgumentException.class,
            () -> db.registerOffice("mario", "  ", "pwd".toCharArray(), Role.JUDGE));
        // doppioni
        assertThrows(IllegalStateException.class, () -> db.register("mario", "pwd".toCharArray(), 99));
        assertThrows(IllegalStateException.class,
            () -> db.registerOffice("giudice", "G-001", "pwd".toCharArray(), Role.JUDGE));
    }

    @Test
    void senzaServiziNessunoOttieneAnnullamentoEPagamento() throws Exception {
        LoginProxy senza = new LoginProxy(new LoginGateway(db), new FullFineHistory(new InMemoryFineDAO()));
        assertFalse(senza.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getCancellation().isPresent());
        UUID qualsiasi = UUID.randomUUID();
        assertThrows(IllegalStateException.class,
            () -> senza.openSession("mario", "pwdMario1!".toCharArray()).pay(qualsiasi));
    }
}
