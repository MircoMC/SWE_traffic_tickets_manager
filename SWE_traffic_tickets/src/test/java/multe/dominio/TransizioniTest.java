package multe.dominio;

import static multe.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import multe.app.OrologioModificabile;
import multe.persistenza.MultaDAOInMemory;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.testutil.GatewayContatore;
import multe.utenti.Giudice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransizioniTest {

    private MultaDAOInMemory dao;
    private GatewayContatore gateway;
    private OrologioModificabile clock;
    private RegistroTarghe registro;
    private ServiziMulte servizi;
    private Annullamento annullamento;

    @BeforeEach
    void setUp() {
        dao = new MultaDAOInMemory();
        gateway = new GatewayContatore();
        clock = new OrologioModificabile();
        registro = new RegistroTargheInMemory(clock);
        registro.assegna("AB123CD", 1);
        registro.assegna("EF456GH", 2);
        servizi = new ServiziMulte(dao, registro, gateway, clock);
        annullamento = servizi.annullamentoPer(new Giudice(10, "giudice"));
    }

    private String statoDao(int id) {
        return dao.trovaPerId(id).get().getState().getState();
    }

    @Test
    void multaAppenaCreataEEmessaESalvata() {
        int id = crea(servizi, clock, "AB123CD", 92);
        assertEquals("EMESSA", servizi.getStato(id).getState());
        assertEquals("EMESSA", statoDao(id));
        assertEquals(57_000L, servizi.getCopia(id).getAmountCents());
        assertEquals(LocalDate.now(clock).plusDays(60), servizi.getCopia(id).getDueDate());
    }

    @Test
    void multaEmessaSiPagaUnaVoltaSola() {
        int id = crea(servizi, clock, "AB123CD", 92);
        assertTrue(servizi.paga(id));
        assertEquals("PAGATA", servizi.getStato(id).getState());
        assertEquals("PAGATA", statoDao(id));

        assertThrows(IllegalStateException.class, () -> servizi.paga(id));
        assertEquals(1, gateway.addebiti());
    }

    @Test
    void pagamentoRifiutatoLasciaLaMultaInvariata() {
        int id = crea(servizi, clock, "AB123CD", 92);
        gateway.rifiuta(true);
        assertFalse(servizi.paga(id));
        assertEquals("EMESSA", servizi.getStato(id).getState());
        assertEquals("EMESSA", statoDao(id));
        assertEquals(0, gateway.addebiti());
    }

    @Test
    void multaPagataNonSiAnnulla() {
        int id = crea(servizi, clock, "AB123CD", 92);
        servizi.paga(id);

        assertThrows(IllegalStateException.class, () -> annullamento.annulla(id, "Ricorso"));
        assertEquals("PAGATA", servizi.getStato(id).getState());
        assertEquals("PAGATA", statoDao(id));
        assertFalse(servizi.getCopia(id).getProvvedimento().isPresent());
    }

    @Test
    void multaEmessaSiAnnullaEResteraAnnullata() {
        int id = crea(servizi, clock, "AB123CD", 92);
        annullamento.annulla(id, "Errore di rilevazione");
        assertEquals("ANNULLATA", servizi.getStato(id).getState());
        assertEquals("ANNULLATA", statoDao(id));

        assertThrows(IllegalStateException.class, () -> servizi.paga(id));
        assertThrows(IllegalStateException.class, () -> annullamento.annulla(id, "Di nuovo"));
        assertEquals(0, gateway.addebiti());
    }

    @Test
    void scadenzaScattaSoloDopoLaData() {
        int id = crea(servizi, clock, "AB123CD", 92);

        servizi.verificaScadenza(id);
        assertEquals("EMESSA", servizi.getStato(id).getState());

        clock.avanza(Duration.ofDays(60));   // l'ultimo giorno non è ancora scaduta
        servizi.verificaScadenza(id);
        assertEquals("EMESSA", servizi.getStato(id).getState());

        clock.avanza(Duration.ofDays(1));
        servizi.verificaScadenza(id);
        assertEquals("SCADUTA", servizi.getStato(id).getState());
        assertEquals("SCADUTA", statoDao(id));
    }

    @Test
    void multaScadutaSiPagaEUnaPagataNonScade() {
        int scaduta = crea(servizi, clock, "AB123CD", 92);
        int pagata = crea(servizi, clock, "EF456GH", 81);
        servizi.paga(pagata);

        clock.avanza(Duration.ofDays(90));
        servizi.verificaScadenza(scaduta);
        servizi.verificaScadenza(pagata);

        assertEquals("SCADUTA", servizi.getStato(scaduta).getState());
        assertEquals("PAGATA", servizi.getStato(pagata).getState());
        assertTrue(servizi.paga(scaduta));
        assertEquals("PAGATA", statoDao(scaduta));
    }

    @Test
    void multaScadutaSiPuoAnnullare() {
        int id = crea(servizi, clock, "AB123CD", 92);
        clock.avanza(Duration.ofDays(61));
        servizi.verificaScadenza(id);
        annullamento.annulla(id, "Ricorso accolto");
        assertEquals("ANNULLATA", servizi.getStato(id).getState());
    }

    @Test
    void intestatarioEIlProprietarioAlMomentoDellaRilevazione() {
        Instant t0 = clock.instant();
        clock.avanza(Duration.ofDays(10));
        registro.assegna("AB123CD", 2);                         // vendita al giorno 10

        // rilevata al giorno 5 ma arrivata solo ora, dopo la vendita: va al vecchio proprietario
        int tardiva = servizi.creaMulta(new Rilevazione("A1", "AB123CD", 80, 50, t0.plus(Duration.ofDays(5))));
        int dopo = crea(servizi, clock, " ab123cd", 80);        // targa normalizzata

        assertEquals(1, servizi.getCopia(tardiva).getIdIntestatario());
        assertEquals(2, servizi.getCopia(dopo).getIdIntestatario());
        assertEquals("AB123CD", servizi.getCopia(dopo).getPlate());
        assertEquals(1, dao.trovaPerIntestatario(1).size());
        assertEquals(1, dao.trovaPerIntestatario(2).size());
    }

    @Test
    void rilevazioneAnterioreAllaRegistrazioneDellaTargaERifiutata() {
        Rilevazione r = new Rilevazione("A1", "AB123CD", 80, 50, clock.instant().minus(Duration.ofDays(1)));
        assertThrows(TargaNonRegistrataException.class, () -> servizi.creaMulta(r));
    }

    @Test
    void senzaInfrazioneNonNasceUnaMulta() {
        assertThrows(IllegalArgumentException.class, () -> crea(servizi, clock, "AB123CD", 50));   // uguale al limite
        assertThrows(IllegalArgumentException.class, () -> crea(servizi, clock, "AB123CD", 30));
        assertThrows(IllegalArgumentException.class, () -> crea(servizi, clock, "AB123CD", 55));   // dentro il margine di misura
        assertTrue(dao.trovaPerIntestatario(1).isEmpty());
        crea(servizi, clock, "AB123CD", 56);                                                       // oltre il margine
        assertEquals(1, dao.trovaPerIntestatario(1).size());
    }

    @Test
    void limiteEImportoVengonoDallaRilevazione() {
        int id = servizi.creaMulta(new Rilevazione("A7", "AB123CD", 92, 70, clock.instant()));
        Multa m = servizi.getCopia(id);
        assertEquals(37_000L, m.getAmountCents());           // 15000 + 22 km/h in eccesso * 1000
        assertEquals("A7", m.getRilevazione().getIdAutovelox());
        assertEquals(70, m.getRilevazione().getLimite());
        assertEquals(92, m.getDetectedSpeed());
    }

    @Test
    void targaNonRegistrataERifiutata() {
        assertThrows(TargaNonRegistrataException.class, () -> crea(servizi, clock, "ZZ000ZZ", 90));
    }

    @Test
    void multaInesistente() {
        assertThrows(IllegalArgumentException.class, () -> servizi.getStato(999));
        assertThrows(IllegalArgumentException.class, () -> servizi.paga(999));
        assertThrows(IllegalArgumentException.class, () -> servizi.getCopia(999));
    }
}
