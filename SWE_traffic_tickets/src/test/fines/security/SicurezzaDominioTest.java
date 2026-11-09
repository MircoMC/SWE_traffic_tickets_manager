package fines.security;

import java.util.UUID;
import static fines.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import fines.domain.FineEvent;
import fines.domain.Fine;
import fines.domain.FineService;
import fines.observers.Observable;
import fines.observers.Observer;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Stanno in un package DIVERSO da fines.domain: simulano chi usa il dominio da fuori,
 * con un osservatore ostile registrato tramite l'API pubblica.
 */
class SicurezzaDominioTest {

    private InMemoryFineDAO dao;
    private FineService servizi;
    private final Map<UUID, Fine> rubate = new ConcurrentHashMap<>();
    private final List<FineEvent> visti = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        PlateRegistry registro = new InMemoryPlateRegistry();
        registro.assign("AB123CD", 1);
        servizi = new FineService(dao, registro, new GatewayContatore());

        servizi.addObserver(new Observer() {          // osservatore "onesto", per confronto
            @Override public void update(Observable source, Object arg) { visti.add((FineEvent) arg); }
        });
        servizi.addObserver(new Observer() {          // osservatore ostile: tiene ciò che riceve
            @Override public void update(Observable source, Object arg) {
                Fine m = (Fine) source;
                rubate.put(m.getId(), m);
            }
        });
    }

    @Test
    void gliOsservatoriRicevonoSoloCopieNonLOriginale() {
        UUID id = crea(servizi, "AB123CD", 80);
        servizi.awaitNotifications();
        Fine ricevuta = rubate.get(id);
        assertNotNull(ricevuta);

        servizi.pay(id);
        // se fosse l'originale, ora direbbe PAID
        assertEquals("ISSUED", ricevuta.getState().getState());
        assertEquals("PAID", servizi.getState(id).getState());
    }

    @Test
    void deleteObserversSuCioCheSiRiceveNonStaccaNessuno() {
        UUID id = crea(servizi, "AB123CD", 80);
        servizi.awaitNotifications();
        rubate.get(id).deleteObservers();

        servizi.pay(id);
        servizi.awaitNotifications();
        assertEquals("PAID", dao.findById(id).get().getState().getState());
        assertEquals(FineEvent.PAID, visti.get(visti.size() - 1));   // gli altri osservatori ricevono ancora gli eventi
    }

    @Test
    void tenereUnLockSuCioCheSiRiceveNonBloccaIlDominio() throws Exception {
        UUID id = crea(servizi, "AB123CD", 80);
        servizi.awaitNotifications();
        Fine ricevuta = rubate.get(id);

        CountDownLatch preso = new CountDownLatch(1);
        CountDownLatch rilascia = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            synchronized (ricevuta) {
                preso.countDown();
                try { rilascia.await(); } catch (InterruptedException ignorata) { }
            }
        });
        t.start();
        assertTrue(preso.await(2, TimeUnit.SECONDS));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> servizi.pay(id));
            assertEquals("PAID", servizi.getState(id).getState());
        } finally {
            rilascia.countDown();
            t.join();
        }
    }

    @Test
    void leCopieDellaFacciataSonoSempreDistinte() {
        UUID id = crea(servizi, "AB123CD", 80);
        servizi.awaitNotifications();
        assertNotSame(servizi.getCopy(id), servizi.getCopy(id));
        assertNotSame(rubate.get(id), servizi.getCopy(id));
    }

    @Test
    void modificareUnaCopiaNonCambiaLaMulta() {
        UUID id = crea(servizi, "AB123CD", 80);
        servizi.awaitNotifications();
        Fine copia = servizi.getCopy(id);
        copia.deleteObservers();
        copia.addObserver((s, a) -> { });
        assertEquals("ISSUED", servizi.getState(id).getState());
        servizi.pay(id);
        assertEquals("ISSUED", copia.getState().getState());
    }

    @Test
    void lostessoOsservatoreNonSiRegistraDueVolte() {
        Observer o = new Observer() {
            @Override public void update(Observable source, Object arg) { visti.add((FineEvent) arg); }
        };
        servizi.addObserver(o);
        servizi.addObserver(o);
        servizi.awaitNotifications();
        int prima = visti.size();
        crea(servizi, "AB123CD", 80);
        servizi.awaitNotifications();
        assertEquals(prima + 2, visti.size());   // l'osservatore del setUp + questo, non tre
    }
}
