package fines.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Collections;
import fines.app.AdjustableClock;
import fines.login.AuthenticationException;
import fines.login.TooManyAttemptsException;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.AttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptLimitTest {

    private AdjustableClock orologio;
    private AttemptLimiter limitatore;
    private LoginProxy login;

    @BeforeEach
    void setUp() {
        orologio = new AdjustableClock();
        limitatore = new AttemptLimiter(orologio);   // 3 errori, blocco 1 minuto, finestra 24 ore
        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("luca", "pwdLuca2!".toCharArray(), 2);
        login = new LoginProxy(new LoginGateway(db), id -> Collections.emptyList(), null, null, orologio);
    }

    private void sbaglia(String utente, String dispositivo) {
        assertThrows(AuthenticationException.class,
            () -> login.openSession(utente, "sbagliata".toCharArray(), dispositivo));
    }

    private void sbagliaTre(String dispositivo) {
        sbaglia("mario", dispositivo);
        sbaglia("luca", dispositivo);
        sbaglia("inesistente", dispositivo);
    }

    // ---- AttemptLimiter da solo ----

    @Test
    void dopoLaSogliaIlBloccoCresceAdOgniErrore() {
        limitatore.registerFailure("d1");
        limitatore.registerFailure("d1");
        assertFalse(limitatore.isBlocked("d1"));
        limitatore.registerFailure("d1");               // terzo: blocco di 1 minuto
        assertTrue(limitatore.isBlocked("d1"));
        orologio.advance(Duration.ofSeconds(59));
        assertTrue(limitatore.isBlocked("d1"));
        orologio.advance(Duration.ofSeconds(2));
        assertFalse(limitatore.isBlocked("d1"));
        limitatore.registerFailure("d1");               // quarto: 2 minuti
        orologio.advance(Duration.ofSeconds(119));
        assertTrue(limitatore.isBlocked("d1"));
        orologio.advance(Duration.ofSeconds(2));
        assertFalse(limitatore.isBlocked("d1"));
        limitatore.registerFailure("d1");               // quinto: 4 minuti
        orologio.advance(Duration.ofSeconds(239));
        assertTrue(limitatore.isBlocked("d1"));
        orologio.advance(Duration.ofSeconds(2));
        assertFalse(limitatore.isBlocked("d1"));
    }

    @Test
    void ilConteggioSiAzzeraDopo24OreDalPrimoErrore() {
        limitatore.registerFailure("d1");
        orologio.advance(Duration.ofHours(23));
        limitatore.registerFailure("d1");
        limitatore.registerFailure("d1");               // terzo errore dentro la finestra: bloccato
        assertTrue(limitatore.isBlocked("d1"));
        orologio.advance(Duration.ofHours(1));              // 24 ore dal primo errore
        assertFalse(limitatore.isBlocked("d1"));
        limitatore.registerFailure("d1");               // si riparte da uno: nessun blocco
        assertFalse(limitatore.isBlocked("d1"));
    }

    @Test
    void ilBloccoNonSuperaMaiLaFinestra() {
        for (int i = 0; i < 40; i++) {
            limitatore.registerFailure("d1");
        }
        orologio.advance(Duration.ofHours(24));
        assertFalse(limitatore.isBlocked("d1"));
    }

    @Test
    void chiaviDiverseSonoIndipendentiEUnaChiaveNullNonSiBlocca() {
        for (int i = 0; i < 5; i++) {
            limitatore.registerFailure("d1");
            limitatore.registerFailure(null);
        }
        assertTrue(limitatore.isBlocked("d1"));
        assertFalse(limitatore.isBlocked("d2"));
        assertFalse(limitatore.isBlocked(null));
    }

    @Test
    void ilSuccessoAzzeraLaChiave() {
        limitatore.registerFailure("a");
        limitatore.registerFailure("a");
        limitatore.registerSuccess("a");
        limitatore.registerFailure("a");
        assertFalse(limitatore.isBlocked("a"));
    }

    // ---- integrazione con LoginProxy ----

    @Test
    void dopoTreErroriIlDispositivoEBloccatoAncheConLaPasswordGiusta() throws Exception {
        sbagliaTre("d1");
        AuthenticationException e = assertThrows(TooManyAttemptsException.class,
            () -> login.openSession("mario", "pwdMario1!".toCharArray(), "d1"));
        assertEquals("Troppi tentativi effettuati, ritenta più tardi", e.getMessage());   // errore distinto: il blocco è del dispositivo
        orologio.advance(Duration.ofSeconds(61));
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "d1"));
    }

    @Test
    void unAltroDispositivoNonEBloccato() throws Exception {
        sbagliaTre("d1");
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "d2"));
    }

    @Test
    void ilSuccessoNonRicaricaITentativiDelDispositivo() throws Exception {
        sbaglia("mario", "d1");
        sbaglia("mario", "d1");
        assertNotNull(login.openSession("luca", "pwdLuca2!".toCharArray(), "d1"));   // un accesso riuscito...
        sbaglia("mario", "d1");                                                 // ...non cancella gli errori: 3° errore
        assertThrows(AuthenticationException.class,
            () -> login.openSession("mario", "pwdMario1!".toCharArray(), "d1"));
    }

    @Test
    void lAccountNonEMaiBloccatoNemmenoDopoTantiErroriDaDispositiviDiversi() throws Exception {
        for (int i = 0; i < 30; i++) {
            sbaglia("mario", "dispositivo-" + i);
        }
        // chi conosce il codice fiscale non può tenere fuori il titolare: il titolare viene avvisato, non bloccato
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "nuovo"));
        assertNotNull(login.openSession("luca", "pwdLuca2!".toCharArray(), "nuovo"));
    }

    @Test
    void senzaDispositivoNonCiSonoBlocchi() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthenticationException.class,
                () -> login.openSession("mario", "sbagliata".toCharArray()));
        }
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray()));
    }

    @Test
    void passwordNullaERifiutataSenzaEccezioniStrane() {
        assertThrows(AuthenticationException.class, () -> login.openSession("mario", null, "d1"));
    }
}
