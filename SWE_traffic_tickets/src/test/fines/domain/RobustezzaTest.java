package fines.domain;

import java.util.UUID;
import static fines.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.DaoFallibile;
import fines.testutil.GatewayContatore;
import fines.testutil.Infrazioni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RobustezzaTest {

    private DaoFallibile dao;
    private GatewayContatore gateway;
    private FineService servizi;

    @BeforeEach
    void setUp() {
        dao = new DaoFallibile();
        gateway = new GatewayContatore();
        PlateRegistry registro = new InMemoryPlateRegistry();
        registro.assign("AB123CD", 1);
        servizi = new FineService(dao, registro, gateway);
    }

    @Test
    void seIlDatabaseFallisceDopoLAddebitoSiPuoRiprovareSenzaPagareDueVolte() {
        UUID id = crea(servizi, "AB123CD", 80);

        dao.fallisciAggiorna(true);
        assertThrows(IllegalStateException.class, () -> servizi.pay(id));
        // la memoria non è mai più avanti del database
        assertEquals("ISSUED", servizi.getState(id).getState());
        assertEquals("ISSUED", dao.findById(id).get().getState().getState());
        assertEquals(1, gateway.addebiti());

        dao.fallisciAggiorna(false);
        assertTrue(servizi.pay(id));
        assertEquals("PAID", servizi.getState(id).getState());
        assertEquals("PAID", dao.findById(id).get().getState().getState());
        assertEquals(1, gateway.addebiti());   // nessun secondo addebito
    }

    @Test
    void unOsservatoreCheLanciaNonFermaGliAltriNeLOperazioneNeIlDao() {
        List<FineEvent> visti = new CopyOnWriteArrayList<>();
        servizi.addObserver(new FineObserver() {          // notificato per ultimo
            @Override protected void onEvent(Fine m, FineEvent e) { visti.add(e); }
        });
        servizi.addObserver(new FineObserver() {          // notificato per primo
            @Override protected void onEvent(Fine m, FineEvent e) { throw new RuntimeException("SMTP non raggiungibile"); }
        });

        UUID id = crea(servizi, "AB123CD", 80);
        assertTrue(servizi.pay(id));
        servizi.awaitNotifications();

        assertEquals(2, visti.size());
        assertEquals(FineEvent.ISSUED, visti.get(0));
        assertEquals(FineEvent.PAID, visti.get(1));
        assertEquals("PAID", dao.findById(id).get().getState().getState());
    }

    @Test
    void pagamentiConcorrentiSuUnaStessaMultaAddebitanoUnaVoltaSola() throws Exception {
        UUID id = crea(servizi, "AB123CD", 80);
        int thread = 16;
        ExecutorService pool = Executors.newFixedThreadPool(thread);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<Boolean>> esiti = new ArrayList<>();
        for (int i = 0; i < thread; i++) {
            esiti.add(pool.submit(() -> {
                via.await();
                try {
                    return servizi.pay(id);
                } catch (IllegalStateException giaPagata) {
                    return false;
                }
            }));
        }
        via.countDown();
        int riusciti = 0;
        for (Future<Boolean> f : esiti) {
            if (f.get(10, TimeUnit.SECONDS)) {
                riusciti++;
            }
        }
        pool.shutdown();

        assertEquals(1, riusciti);
        assertEquals(1, gateway.addebiti());
        assertEquals("PAID", servizi.getState(id).getState());
    }

    @Test
    void multeDiverseSiPaganoIndipendentemente() {
        UUID a = crea(servizi, "AB123CD", 80);
        UUID b = crea(servizi, "AB123CD", 90);
        servizi.pay(a);
        assertEquals("PAID", servizi.getState(a).getState());
        assertEquals("ISSUED", servizi.getState(b).getState());
        assertEquals(1, gateway.addebiti());
    }

    @Test
    void unOsservatoreLentoNonTieneBloccataLaMultaNeLeAltre() throws Exception {
        CountDownLatch sblocca = new CountDownLatch(1);
        List<FineEvent> consegnati = new CopyOnWriteArrayList<>();
        servizi.addObserver(new FineObserver() {
            @Override protected void onEvent(Fine m, FineEvent e) {
                if (e == FineEvent.PAID) {
                    try { sblocca.await(); } catch (InterruptedException x) { Thread.currentThread().interrupt(); }
                }
                consegnati.add(e);
            }
        });
        UUID a = servizi.createFine(Infrazioni.di("AB123CD", 80, java.time.Clock.systemUTC()));
        UUID b = servizi.createFine(Infrazioni.di("AB123CD", 90, java.time.Clock.systemUTC()));

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            servizi.pay(a);                                       // l'osservatore resta bloccato, l'operazione no
            assertEquals("PAID", servizi.getState(a).getState());
            servizi.pay(b);                                       // e le altre multe procedono
            assertEquals("PAID", servizi.getState(b).getState());
        });

        sblocca.countDown();
        servizi.awaitNotifications();
        // le notifiche arrivano tutte e nell'ordine in cui sono accadute
        assertEquals(Arrays.asList(FineEvent.ISSUED, FineEvent.ISSUED, FineEvent.PAID, FineEvent.PAID), consegnati);
    }

    @Test
    void unOsservatoreCheRichiamaLaFacciataSuUnAltraMultaNonCreaDeadlock() throws Exception {
        UUID[] ids = new UUID[2];
        servizi.addObserver(new FineObserver() {
            @Override protected void onEvent(Fine m, FineEvent e) {
                if (e == FineEvent.PAID) {
                    servizi.getState(m.getId().equals(ids[0]) ? ids[1] : ids[0]);      // serve il lock dell'altra multa
                }
            }
        });
        UUID a = servizi.createFine(Infrazioni.di("AB123CD", 80, java.time.Clock.systemUTC()));
        UUID b = servizi.createFine(Infrazioni.di("AB123CD", 90, java.time.Clock.systemUTC()));
        ids[0] = a;
        ids[1] = b;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                Future<Boolean> f1 = pool.submit(() -> servizi.pay(a));
                Future<Boolean> f2 = pool.submit(() -> servizi.pay(b));
                assertTrue(f1.get());
                assertTrue(f2.get());
            });
            servizi.awaitNotifications();
        } finally {
            pool.shutdownNow();
        }
    }
}
