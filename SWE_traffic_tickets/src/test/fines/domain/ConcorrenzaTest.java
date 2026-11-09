package fines.domain;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import fines.observers.Observer;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import fines.testutil.Infrazioni;
import org.junit.jupiter.api.Test;

/**
 * Stanno nel package del dominio perché montano a mano IdentityMap, FineMaker e StateManager,
 * per forzare in modo deterministico gli intrecci tra thread che a livello di facciata sono troppo rapidi.
 */
class ConcorrenzaTest {

    private final Clock clock = Clock.systemUTC();

    private PlateRegistry registro() {
        PlateRegistry r = new InMemoryPlateRegistry(clock);
        r.assign("AB123CD", 1);
        return r;
    }

    private static void aspettaBloccatoOTerminato(Thread t) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (t.getState() != Thread.State.BLOCKED && t.getState() != Thread.State.TERMINATED
               && System.nanoTime() < limite) {
            Thread.sleep(1);
        }
    }

    @Test
    void getCopiaPrendeIlLockDellaMultaEQuindiNonLeggeATranseizioneInCorso() throws Exception {
        IdentityMap mapper = new IdentityMap();
        FineMaker maker = new FineMaker(mapper, new InMemoryFineDAO(), registro(), clock);
        UUID id = maker.createFine(Infrazioni.di("AB123CD", 80, clock));
        Fine originale = mapper.get(id);

        CountDownLatch preso = new CountDownLatch(1);
        CountDownLatch rilascia = new CountDownLatch(1);
        Thread chiTieneIlLock = new Thread(() -> {
            synchronized (originale.lock) {      // come una transizione in corso
                preso.countDown();
                try { rilascia.await(); } catch (InterruptedException ignorata) { }
            }
        });
        chiTieneIlLock.start();
        assertTrue(preso.await(2, TimeUnit.SECONDS));

        Fine[] letta = new Fine[1];
        Thread lettore = new Thread(() -> letta[0] = mapper.getCopy(id));
        lettore.start();
        aspettaBloccatoOTerminato(lettore);
        assertEquals(Thread.State.BLOCKED, lettore.getState());   // aspetta la fine della transizione

        rilascia.countDown();
        lettore.join(5000);
        chiTieneIlLock.join(5000);
        assertNotNull(letta[0]);
    }

    @Test
    void emessaVieneAnnunciataPrimaDiQualsiasiAltraOperazioneSullaMultaAppenaRegistrata() throws Exception {
        InMemoryFineDAO dao = new InMemoryFineDAO();
        GatewayContatore gateway = new GatewayContatore();
        ExecutorService notifiche = Executors.newSingleThreadExecutor();
        List<FineEvent> eventi = Collections.synchronizedList(new ArrayList<>());
        StateManager[] stateManager = new StateManager[1];
        Thread[] pagatore = new Thread[1];

        // IdentityMap che, subito dopo la registrazione, lancia un altro thread a pagare quella multa
        // e aspetta che sia bloccato (lock rispettato) o già finito (lock ignorato)
        IdentityMap mapper = new IdentityMap() {
            @Override
            void register(Fine m) {
                super.register(m);
                Thread altro = new Thread(() -> {
                    try { stateManager[0].paymentStarting(m.getId()); } catch (RuntimeException ignorata) { }
                });
                pagatore[0] = altro;
                altro.start();
                try { aspettaBloccatoOTerminato(altro); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };
        stateManager[0] = new StateManager(mapper, gateway, dao, clock);
        FineMaker maker = new FineMaker(mapper, dao, registro(), clock);
        Observer osservatore = (src, arg) -> eventi.add((FineEvent) arg);
        maker.addObserver(new ProtectedObserver(osservatore, notifiche));

        maker.createFine(Infrazioni.di("AB123CD", 80, clock));
        pagatore[0].join(5000);          // il pagamento deve essere finito prima di chiudere la coda

        notifiche.shutdown();
        assertTrue(notifiche.awaitTermination(5, TimeUnit.SECONDS));
        // il pagamento aspetta la fine dell'annuncio: ISSUED, poi PAID
        assertEquals(Arrays.asList(FineEvent.ISSUED, FineEvent.PAID), eventi);
    }

    /**
     * Gli id non sono più indovinabili: chi paga li riceve dal creatore, dopo la creazione.
     * Controlla che con creazione e pagamento in parallelo ogni multa riceva gli eventi in ordine e senza perdite.
     */
    @Test
    void creazioneEPagamentoInParalleloNonPerdonoNeInvertonoGliEventi() throws Exception {
        final int n = 20_000;
        InMemoryFineDAO dao = new InMemoryFineDAO();
        FineService servizi = new FineService(dao, registro(), new GatewayContatore(), clock);
        Map<UUID, List<FineEvent>> eventi = new ConcurrentHashMap<>();
        servizi.addObserver((src, arg) ->
            eventi.computeIfAbsent(((Fine) src).getId(), k -> Collections.synchronizedList(new ArrayList<>())).add((FineEvent) arg));
        BlockingQueue<UUID> create = new LinkedBlockingQueue<>();

        Thread creatore = new Thread(() -> {
            for (int i = 0; i < n; i++) create.add(servizi.createFine(Infrazioni.di("AB123CD", 90, clock)));
        });
        Thread pagatore = new Thread(() -> {
            try {
                for (int i = 0; i < n; i++) servizi.pay(create.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        creatore.start();
        pagatore.start();
        creatore.join();
        pagatore.join();
        servizi.awaitNotifications();

        for (Map.Entry<UUID, List<FineEvent>> e : eventi.entrySet()) {
            assertEquals(Arrays.asList(FineEvent.ISSUED, FineEvent.PAID), e.getValue());
        }
        assertEquals(n, eventi.size());
    }
}
