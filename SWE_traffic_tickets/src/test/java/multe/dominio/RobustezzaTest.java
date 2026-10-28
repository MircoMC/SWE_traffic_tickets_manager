package multe.dominio;

import static multe.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.DaoFallibile;
import multe.testutil.GatewayContatore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RobustezzaTest {

    private DaoFallibile dao;
    private GatewayContatore gateway;
    private ServiziMulte servizi;

    @BeforeEach
    void setUp() {
        dao = new DaoFallibile();
        gateway = new GatewayContatore();
        RegistroTarghe registro = new RegistroTargheInMemory();
        registro.assegna("AB123CD", 1);
        servizi = new ServiziMulte(dao, registro, gateway);
    }

    @Test
    void seIlDatabaseFallisceDopoLAddebitoSiPuoRiprovareSenzaPagareDueVolte() {
        int id = crea(servizi, "AB123CD", 80);

        dao.fallisciAggiorna(true);
        assertThrows(IllegalStateException.class, () -> servizi.paga(id));
        // la memoria non è mai più avanti del database
        assertEquals("EMESSA", servizi.getStato(id).getState());
        assertEquals("EMESSA", dao.trovaPerId(id).get().getState().getState());
        assertEquals(1, gateway.addebiti());

        dao.fallisciAggiorna(false);
        assertTrue(servizi.paga(id));
        assertEquals("PAGATA", servizi.getStato(id).getState());
        assertEquals("PAGATA", dao.trovaPerId(id).get().getState().getState());
        assertEquals(1, gateway.addebiti());   // nessun secondo addebito
    }

    @Test
    void unOsservatoreCheLanciaNonFermaGliAltriNeLOperazioneNeIlDao() {
        List<Evento> visti = new CopyOnWriteArrayList<>();
        servizi.aggiungiOsservatore(new MultaObserver() {          // notificato per ultimo
            @Override protected void onEvento(Multa m, Evento e) { visti.add(e); }
        });
        servizi.aggiungiOsservatore(new MultaObserver() {          // notificato per primo
            @Override protected void onEvento(Multa m, Evento e) { throw new RuntimeException("SMTP non raggiungibile"); }
        });

        int id = crea(servizi, "AB123CD", 80);
        assertTrue(servizi.paga(id));

        assertEquals(2, visti.size());
        assertEquals(Evento.EMESSA, visti.get(0));
        assertEquals(Evento.PAGATA, visti.get(1));
        assertEquals("PAGATA", dao.trovaPerId(id).get().getState().getState());
    }

    @Test
    void pagamentiConcorrentiSuUnaStessaMultaAddebitanoUnaVoltaSola() throws Exception {
        int id = crea(servizi, "AB123CD", 80);
        int thread = 16;
        ExecutorService pool = Executors.newFixedThreadPool(thread);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<Boolean>> esiti = new ArrayList<>();
        for (int i = 0; i < thread; i++) {
            esiti.add(pool.submit(() -> {
                via.await();
                try {
                    return servizi.paga(id);
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
        assertEquals("PAGATA", servizi.getStato(id).getState());
    }

    @Test
    void multeDiverseSiPaganoIndipendentemente() {
        int a = crea(servizi, "AB123CD", 80);
        int b = crea(servizi, "AB123CD", 90);
        servizi.paga(a);
        assertEquals("PAGATA", servizi.getStato(a).getState());
        assertEquals("EMESSA", servizi.getStato(b).getState());
        assertEquals(1, gateway.addebiti());
    }
}
