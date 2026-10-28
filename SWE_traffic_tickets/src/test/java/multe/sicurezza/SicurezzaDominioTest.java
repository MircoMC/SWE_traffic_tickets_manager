package multe.sicurezza;

import static multe.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import multe.dominio.Evento;
import multe.dominio.Multa;
import multe.dominio.ServiziMulte;
import multe.osservatori.Observable;
import multe.osservatori.Observer;
import multe.persistenza.MultaDAOInMemory;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.GatewayContatore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Stanno in un package DIVERSO da multe.dominio: simulano chi usa il dominio da fuori,
 * con un osservatore ostile registrato tramite l'API pubblica.
 */
class SicurezzaDominioTest {

    private MultaDAOInMemory dao;
    private ServiziMulte servizi;
    private final Map<Integer, Multa> rubate = new ConcurrentHashMap<>();
    private final List<Evento> visti = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        dao = new MultaDAOInMemory();
        RegistroTarghe registro = new RegistroTargheInMemory();
        registro.assegna("AB123CD", 1);
        servizi = new ServiziMulte(dao, registro, new GatewayContatore());

        servizi.aggiungiOsservatore(new Observer() {          // osservatore "onesto", per confronto
            @Override public void update(Observable source, Object arg) { visti.add((Evento) arg); }
        });
        servizi.aggiungiOsservatore(new Observer() {          // osservatore ostile: tiene ciò che riceve
            @Override public void update(Observable source, Object arg) {
                Multa m = (Multa) source;
                rubate.put(m.getId(), m);
            }
        });
    }

    @Test
    void gliOsservatoriRicevonoSoloCopieNonLOriginale() {
        int id = crea(servizi, "AB123CD", 80);
        Multa ricevuta = rubate.get(id);
        assertNotNull(ricevuta);

        servizi.paga(id);
        // se fosse l'originale, ora direbbe PAGATA
        assertEquals("EMESSA", ricevuta.getState().getState());
        assertEquals("PAGATA", servizi.getStato(id).getState());
    }

    @Test
    void deleteObserversSuCioCheSiRiceveNonStaccaNessuno() {
        int id = crea(servizi, "AB123CD", 80);
        rubate.get(id).deleteObservers();

        servizi.paga(id);
        assertEquals("PAGATA", dao.trovaPerId(id).get().getState().getState());
        assertEquals(Evento.PAGATA, visti.get(visti.size() - 1));   // gli altri osservatori ricevono ancora gli eventi
    }

    @Test
    void tenereUnLockSuCioCheSiRiceveNonBloccaIlDominio() throws Exception {
        int id = crea(servizi, "AB123CD", 80);
        Multa ricevuta = rubate.get(id);

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
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> servizi.paga(id));
            assertEquals("PAGATA", servizi.getStato(id).getState());
        } finally {
            rilascia.countDown();
            t.join();
        }
    }

    @Test
    void leCopieDellaFacciataSonoSempreDistinte() {
        int id = crea(servizi, "AB123CD", 80);
        assertNotSame(servizi.getCopia(id), servizi.getCopia(id));
        assertNotSame(rubate.get(id), servizi.getCopia(id));
    }

    @Test
    void modificareUnaCopiaNonCambiaLaMulta() {
        int id = crea(servizi, "AB123CD", 80);
        Multa copia = servizi.getCopia(id);
        copia.deleteObservers();
        copia.addObserver((s, a) -> { });
        assertEquals("EMESSA", servizi.getStato(id).getState());
        servizi.paga(id);
        assertEquals("EMESSA", copia.getState().getState());
    }

    @Test
    void lostessoOsservatoreNonSiRegistraDueVolte() {
        Observer o = new Observer() {
            @Override public void update(Observable source, Object arg) { visti.add((Evento) arg); }
        };
        servizi.aggiungiOsservatore(o);
        servizi.aggiungiOsservatore(o);
        int prima = visti.size();
        crea(servizi, "AB123CD", 80);
        assertEquals(prima + 2, visti.size());   // l'osservatore del setUp + questo, non tre
    }
}
