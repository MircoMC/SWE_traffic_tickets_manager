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

/** L'account non si blocca mai: si avvisa il titolare, una volta sola per finestra. */
class SorveglianzaAccountTest {

    private AdjustableClock orologio;
    private AccountMonitor sorveglianza;      // soglia 10, finestra 24 ore

    @BeforeEach
    void setUp() {
        orologio = new AdjustableClock();
        sorveglianza = new AccountMonitor(orologio);
    }

    @Test
    void avvisaAllaSogliaEUnaVoltaSola() {
        for (int i = 1; i <= 9; i++) assertEquals(0, sorveglianza.registerFailure("C:mario"));
        assertEquals(10, sorveglianza.registerFailure("C:mario"));     // la soglia: si avvisa
        for (int i = 0; i < 50; i++) assertEquals(0, sorveglianza.registerFailure("C:mario"));   // poi silenzio
    }

    @Test
    void dopoLaFinestraSiRiparteEAvvisaDiNuovo() {
        for (int i = 0; i < 10; i++) sorveglianza.registerFailure("C:mario");
        orologio.advance(Duration.ofHours(24));
        for (int i = 1; i <= 9; i++) assertEquals(0, sorveglianza.registerFailure("C:mario"));
        assertEquals(10, sorveglianza.registerFailure("C:mario"));
    }

    @Test
    void unAccessoRiuscitoRiarmaLAvvisoPerchePuoFarloSoloIlTitolare() {
        for (int i = 0; i < 10; i++) sorveglianza.registerFailure("C:mario");
        sorveglianza.registerSuccess("C:mario");
        for (int i = 1; i <= 9; i++) assertEquals(0, sorveglianza.registerFailure("C:mario"));
        assertEquals(10, sorveglianza.registerFailure("C:mario"));
    }

    @Test
    void leChiaviSonoIndipendentiELaNullaNonConta() {
        for (int i = 0; i < 9; i++) {
            sorveglianza.registerFailure("C:mario");
            sorveglianza.registerFailure("R:mario");
            assertEquals(0, sorveglianza.registerFailure(null));
        }
        assertEquals(10, sorveglianza.registerFailure("C:mario"));
        assertEquals(10, sorveglianza.registerFailure("R:mario"));
    }

    @Test
    void conTantiErroriInParalleloLAvvisoPartePerUnaVoltaSola() throws Exception {
        int n = 200;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<Integer>> esiti = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            esiti.add(pool.submit(() -> { via.await(); return sorveglianza.registerFailure("C:mario"); }));
        }
        via.countDown();
        int avvisi = 0;
        for (Future<Integer> f : esiti) {
            if (f.get(10, TimeUnit.SECONDS) > 0) avvisi++;
        }
        pool.shutdown();
        assertEquals(1, avvisi);            // nessun modo di inondare di email il titolare
    }

    @Test
    void ilNumeroDiChiaviHaUnTetto() {
        AccountMonitor piccola = new AccountMonitor(orologio, 10, Duration.ofHours(24), 50);
        for (int i = 0; i < 1_000; i++) {
            piccola.registerFailure("C:inventato-" + i);
        }
        assertTrue(piccola.trackedKeys() <= 50);
    }
}
