package fines.login;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import fines.app.AdjustableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Prenotazione atomica dei tentativi e tetto al numero di chiavi. */
class LimitatoreTentativiAtomicoTest {

    private AdjustableClock orologio;
    private AttemptLimiter limitatore;     // soglia 3, blocco iniziale 1 minuto

    @BeforeEach
    void setUp() {
        orologio = new AdjustableClock();
        limitatore = new AttemptLimiter(orologio);
    }

    @Test
    void conTentativiInParalleloPassanoSoloQuelliDellaSoglia() throws Exception {
        int n = 60;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<Boolean>> esiti = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            esiti.add(pool.submit(() -> { via.await(); return limitatore.tryAttempt("d1"); }));
        }
        via.countDown();
        int ammessi = 0;
        for (Future<Boolean> f : esiti) {
            if (f.get(10, TimeUnit.SECONDS)) ammessi++;
        }
        pool.shutdown();
        assertEquals(3, ammessi);          // la soglia, non il numero di connessioni parallele
        assertTrue(limitatore.isBlocked("d1"));
    }

    @Test
    void iTentativiRifiutatiPerBloccoNonVengonoContati() {
        for (int i = 0; i < 3; i++) assertTrue(limitatore.tryAttempt("d1"));     // il 3° fa scattare il blocco di 1 minuto
        for (int i = 0; i < 20; i++) assertFalse(limitatore.tryAttempt("d1"));   // rifiutati: non contano

        orologio.advance(Duration.ofSeconds(61));
        assertTrue(limitatore.tryAttempt("d1"));                                  // 4° errore contato: blocco di 2 minuti
        orologio.advance(Duration.ofSeconds(121));
        assertFalse(limitatore.isBlocked("d1"));                                   // se i 20 rifiutati contassero, durerebbe molto di più
    }

    @Test
    void ilSuccessoRestituisceIlTentativoSenzaRicaricareGliAltri() {
        assertTrue(limitatore.tryAttempt("d1"));      // 1
        assertTrue(limitatore.tryAttempt("d1"));      // 2
        limitatore.release("d1");                       // un accesso riuscito: torna a 1
        assertTrue(limitatore.tryAttempt("d1"));      // 2
        assertTrue(limitatore.tryAttempt("d1"));      // 3: blocco
        assertFalse(limitatore.tryAttempt("d1"));
    }

    @Test
    void ilSuccessoNonAzzeraGliErroriPrecedenti() {
        assertTrue(limitatore.tryAttempt("d1"));
        assertTrue(limitatore.tryAttempt("d1"));      // due errori
        assertTrue(limitatore.tryAttempt("d1"));      // il terzo tentativo riesce...
        limitatore.release("d1");                       // ...e viene restituito: restano 2 errori, non 0
        assertTrue(limitatore.tryAttempt("d1"));      // 3° errore: blocco
        assertFalse(limitatore.tryAttempt("d1"));
    }

    @Test
    void rilasciareTogliIlBloccoCheQuelTentativoAvevaCausato() {
        assertTrue(limitatore.tryAttempt("d1"));
        assertTrue(limitatore.tryAttempt("d1"));
        assertTrue(limitatore.tryAttempt("d1"));      // 3°: blocco
        assertTrue(limitatore.isBlocked("d1"));
        limitatore.release("d1");                       // ma era un accesso riuscito
        assertFalse(limitatore.isBlocked("d1"));
        assertTrue(limitatore.tryAttempt("d1"));
    }

    @Test
    void laChiaveNullaNonSiBloccaMai() {
        for (int i = 0; i < 100; i++) assertTrue(limitatore.tryAttempt(null));
        limitatore.release(null);                       // nessuna eccezione
        assertEquals(0, limitatore.trackedKeys());
    }

    @Test
    void ilNumeroDiChiaviHaUnTetto() {
        AttemptLimiter piccolo = new AttemptLimiter(orologio, 3, Duration.ofMinutes(1), Duration.ofHours(24), 100);
        for (int i = 0; i < 2_000; i++) {
            piccolo.tryAttempt("chiave-" + i);
        }
        assertTrue(piccolo.trackedKeys() <= 100);
    }

    @Test
    void unaChiaveBloccataNonSiLiberaSommergendoIlLimitatoreDiChiaviNuove() {
        AttemptLimiter piccolo = new AttemptLimiter(orologio, 3, Duration.ofMinutes(1), Duration.ofHours(24), 100);
        for (int i = 0; i < 3; i++) piccolo.tryAttempt("attaccante");
        assertTrue(piccolo.isBlocked("attaccante"));

        for (int i = 0; i < 5_000; i++) {
            piccolo.tryAttempt("rumore-" + i);
        }
        assertTrue(piccolo.isBlocked("attaccante"));
        assertFalse(piccolo.tryAttempt("attaccante"));
    }

    @Test
    void parametriNonValidi() {
        assertThrows(IllegalArgumentException.class, () -> new AttemptLimiter(orologio, 0, Duration.ofMinutes(1), Duration.ofHours(24)));
        assertThrows(IllegalArgumentException.class, () -> new AttemptLimiter(orologio, 3, Duration.ofMinutes(1), Duration.ofHours(24), 0));
    }
}
