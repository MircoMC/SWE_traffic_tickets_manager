package fines.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import fines.app.AdjustableClock;
import fines.login.AuthenticationException;
import fines.login.AccessAlert;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Identity;
import fines.login.Role;
import fines.login.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cosa succede ai tentativi falliti: il DISPOSITIVO si blocca (errore distinto), l'ACCOUNT non si blocca mai
 * ma il titolare viene avvisato, una volta sola, senza rallentare né far fallire gli accessi.
 */
class AvvisoTentativiTest {

    private AdjustableClock orologio;
    private PasswordDatabase db;
    private final List<String> avvisi = new CopyOnWriteArrayList<>();
    private final AtomicInteger valutati = new AtomicInteger();
    private LoginProxy login;

    @BeforeEach
    void setUp() {
        orologio = new AdjustableClock();
        db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("luca", "pwdLuca2!".toCharArray(), 2);
        db.registerOffice("luca", "G-001", "pwdCaricaLuca!".toCharArray(), Role.JUDGE);
        LoginGateway conta = new LoginGateway(db) {          // conta quanti tentativi arrivano davvero al controllo password
            @Override public Optional<Identity> authenticate(String cf, char[] pw) {
                valutati.incrementAndGet();
                return super.authenticate(cf, pw);
            }
        };
        login = new LoginProxy(conta, id -> Collections.emptyList(), null, null, orologio,
            (cf, carica, n) -> avvisi.add(cf + "|" + (carica ? "carica" : "privato") + "|" + n));
    }

    private void sbaglia(String cf, String dispositivo) {
        assertThrows(AuthenticationException.class, () -> login.openSession(cf, "sbagliata".toCharArray(), dispositivo));
    }

    // ---- errore distinto per il blocco del dispositivo

    @Test
    void ilDispositivoBloccatoRiceveUnErroreDistintoELaPasswordNonVieneControllata() throws Exception {
        sbaglia("mario", "d1");
        sbaglia("mario", "d1");
        sbaglia("mario", "d1");
        int prima = valutati.get();

        TooManyAttemptsException e = assertThrows(TooManyAttemptsException.class,
            () -> login.openSession("mario", "pwdMario1!".toCharArray(), "d1"));    // anche con la password giusta
        assertEquals("Troppi tentativi effettuati, ritenta più tardi", e.getMessage());
        assertEquals(prima, valutati.get());                                    // nemmeno controllata
        assertTrue(e instanceof AuthenticationException);

        orologio.advance(Duration.ofSeconds(61));
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "d1"));
    }

    @Test
    void lePasswordSbagliateRestanoConIlMessaggioGenerico() {
        AuthenticationException e = assertThrows(AuthenticationException.class,
            () -> login.openSession("mario", "sbagliata".toCharArray(), "d1"));
        assertEquals("Credenziali non valide", e.getMessage());
        assertFalse(e instanceof TooManyAttemptsException);
    }

    @Test
    void ilBloccoDelDispositivoNonDipendeDaiCodiciFiscaliProvati() {
        // tre errori su account diversi, anche inesistenti, bastano: dire "troppi tentativi" non rivela nessun account
        sbaglia("mario", "d1");
        sbaglia("inesistente", "d1");
        sbaglia("luca", "d1");
        assertThrows(TooManyAttemptsException.class, () -> login.openSession("qualunque", "x".toCharArray(), "d1"));
    }

    @Test
    void unAltroDispositivoNonEBloccato() throws Exception {
        sbaglia("mario", "d1");
        sbaglia("mario", "d1");
        sbaglia("mario", "d1");
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "d2"));
    }

    @Test
    void ilBloccoRegeAncheConLaCarica() {
        for (int i = 0; i < 3; i++) {
            assertThrows(AuthenticationException.class, () -> login.openOfficeSession("luca", "G-001", "sbagliata".toCharArray(), "d1"));
        }
        assertThrows(TooManyAttemptsException.class,
            () -> login.openOfficeSession("luca", "G-001", "pwdCaricaLuca!".toCharArray(), "d1"));
    }

    // ---- il tentativo si prenota PRIMA di controllare la password: nessun aggiramento in parallelo

    @Test
    void conTentativiInParalleloArrivanoAlControlloSoloQuelliDellaSoglia() throws Exception {
        int n = 40;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<String>> esiti = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            esiti.add(pool.submit(() -> {
                via.await();
                try {
                    login.openSession("mario", "sbagliata".toCharArray(), "d1");
                    return "riuscito";
                } catch (TooManyAttemptsException e) {
                    return "bloccato";
                } catch (AuthenticationException e) {
                    return "credenziali";
                }
            }));
        }
        via.countDown();
        int credenziali = 0, bloccati = 0;
        for (Future<String> f : esiti) {
            String esito = f.get(30, TimeUnit.SECONDS);
            if (esito.equals("credenziali")) credenziali++;
            if (esito.equals("bloccato")) bloccati++;
        }
        pool.shutdown();
        assertEquals(3, valutati.get());          // la soglia, non le 40 connessioni
        assertEquals(3, credenziali);
        assertEquals(n - 3, bloccati);
    }

    @Test
    void ilSuccessoRestituisceIlTentativoENonRicaricaGliAltri() throws Exception {
        sbaglia("mario", "d1");
        sbaglia("mario", "d1");
        assertNotNull(login.openSession("luca", "pwdLuca2!".toCharArray(), "d1"));    // accesso riuscito dallo stesso dispositivo
        sbaglia("mario", "d1");                                                  // 3° errore: blocco
        assertThrows(TooManyAttemptsException.class, () -> login.openSession("mario", "pwdMario1!".toCharArray(), "d1"));
    }

    @Test
    void ilSuccessoNonConsumaITentativiDelDispositivo() throws Exception {
        // una rete condivisa: tanti utenti legittimi che entrano dallo stesso dispositivo non lo bloccano
        for (int i = 0; i < 10; i++) {
            assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "ufficio"));
        }
        sbaglia("mario", "ufficio");
        assertNotNull(login.openSession("luca", "pwdLuca2!".toCharArray(), "ufficio"));    // un solo errore: ancora libero
    }

    // ---- l'account non si blocca: si avvisa il titolare

    @Test
    void dopoDieciErroriDaDispositiviDiversiIlTitolareVieneAvvisatoUnaVoltaESenzaBlocco() throws Exception {
        for (int i = 0; i < 30; i++) {
            sbaglia("mario", "dispositivo-" + i);
        }
        login.awaitAlerts();

        assertEquals(Collections.singletonList("mario|privato|10"), avvisi);               // una sola email, non trenta
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "nuovo"));          // l'account non è bloccato
    }

    @Test
    void unAccessoDelTitolareRiarmaLAvviso() throws Exception {
        for (int i = 0; i < 10; i++) sbaglia("mario", "a-" + i);
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray(), "nuovo"));
        for (int i = 0; i < 10; i++) sbaglia("mario", "b-" + i);
        login.awaitAlerts();
        assertEquals(2, avvisi.size());
    }

    @Test
    void accountPrivatoECaricaHannoContatoriEAvvisiSeparati() throws Exception {
        for (int i = 0; i < 9; i++) {
            sbaglia("luca", "p-" + i);
            assertThrows(AuthenticationException.class,
                () -> login.openOfficeSession("luca", "G-001", "sbagliata".toCharArray()));
        }
        login.awaitAlerts();
        assertTrue(avvisi.isEmpty());                                       // 9 + 9: nessuno dei due ha raggiunto 10

        assertThrows(AuthenticationException.class, () -> login.openOfficeSession("luca", "G-001", "sbagliata".toCharArray()));
        login.awaitAlerts();
        assertEquals(Collections.singletonList("luca|carica|10"), avvisi);
    }

    @Test
    void ilTitolareNonConoscibileDaChiProvaIlProxyAvvisaAncheCodiciFiscaliInesistenti() {
        for (int i = 0; i < 10; i++) sbaglia("inesistente", "x-" + i);
        login.awaitAlerts();
        // il proxy non sa se l'account esiste e non deve farlo capire: sta a chi manda l'email non trovare il recapito
        assertEquals(Collections.singletonList("inesistente|privato|10"), avvisi);
    }

    @Test
    void unAvvisoCheLanciaUnaEccezioneNonRompeGliAccessi() throws Exception {
        List<String> ricevuti = new CopyOnWriteArrayList<>();
        AccessAlert rotto = (cf, carica, n) -> {
            ricevuti.add(cf);
            throw new IllegalStateException("server di posta non raggiungibile");
        };
        LoginProxy l = new LoginProxy(new LoginGateway(db), id -> Collections.emptyList(), null, null, orologio, rotto);
        for (int i = 0; i < 10; i++) {
            int k = i;
            AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> l.openSession("mario", "sbagliata".toCharArray(), "d-" + k));
            assertEquals("Credenziali non valide", e.getMessage());            // sempre l'errore normale
        }
        l.awaitAlerts();
        assertEquals(1, ricevuti.size());
        assertNotNull(l.openSession("mario", "pwdMario1!".toCharArray(), "nuovo"));
    }

    @Test
    void unAvvisoLentoNonRallentaGliAccessi() throws Exception {
        CountDownLatch sblocca = new CountDownLatch(1);
        AccessAlert lento = (cf, carica, n) -> {
            try { sblocca.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };
        LoginProxy l = new LoginProxy(new LoginGateway(db), id -> Collections.emptyList(), null, null, orologio, lento);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int i = 0; i < 10; i++) {                     // il 10° fa partire l'avviso, che resta bloccato
                int k = i;
                assertThrows(AuthenticationException.class, () -> l.openSession("mario", "sbagliata".toCharArray(), "d-" + k));
            }
            assertNotNull(l.openSession("mario", "pwdMario1!".toCharArray(), "nuovo"));    // gli accessi proseguono
        });
        sblocca.countDown();
        l.awaitAlerts();
    }

    @Test
    void senzaDispositivoNonCiSonoBlocchiMaGliErroriContanoPerLAvviso() throws Exception {
        for (int i = 0; i < 12; i++) {
            assertThrows(AuthenticationException.class, () -> login.openSession("mario", "sbagliata".toCharArray()));
        }
        login.awaitAlerts();
        assertEquals(Collections.singletonList("mario|privato|10"), avvisi);
        assertNotNull(login.openSession("mario", "pwdMario1!".toCharArray()));
    }
}
