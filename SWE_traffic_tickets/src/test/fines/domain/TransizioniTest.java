package fines.domain;

import java.util.UUID;
import static fines.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import fines.app.AdjustableClock;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import fines.users.Judge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransizioniTest {

    private InMemoryFineDAO dao;
    private GatewayContatore gateway;
    private AdjustableClock clock;
    private PlateRegistry registro;
    private FineService servizi;
    private Cancellation annullamento;

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        gateway = new GatewayContatore();
        clock = new AdjustableClock();
        registro = new InMemoryPlateRegistry(clock);
        registro.assign("AB123CD", 1);
        registro.assign("EF456GH", 2);
        servizi = new FineService(dao, registro, gateway, clock);
        annullamento = servizi.cancellationFor(new Judge(10, "giudice"));
    }

    private String statoDao(UUID id) {
        return dao.findById(id).get().getState().getState();
    }

    @Test
    void multaAppenaCreataEEmessaESalvata() {
        UUID id = crea(servizi, clock, "AB123CD", 92);
        assertEquals("ISSUED", servizi.getState(id).getState());
        assertEquals("ISSUED", statoDao(id));
        assertEquals(57_000L, servizi.getCopy(id).getAmountCents());
        assertEquals(LocalDate.now(clock).plusDays(60), servizi.getCopy(id).getDueDate());
    }

    @Test
    void multaEmessaSiPagaUnaVoltaSola() {
        UUID id = crea(servizi, clock, "AB123CD", 92);
        assertTrue(servizi.pay(id));
        assertEquals("PAID", servizi.getState(id).getState());
        assertEquals("PAID", statoDao(id));

        assertThrows(IllegalStateException.class, () -> servizi.pay(id));
        assertEquals(1, gateway.addebiti());
    }

    @Test
    void pagamentoRifiutatoLasciaLaMultaInvariata() {
        UUID id = crea(servizi, clock, "AB123CD", 92);
        gateway.rifiuta(true);
        assertFalse(servizi.pay(id));
        assertEquals("ISSUED", servizi.getState(id).getState());
        assertEquals("ISSUED", statoDao(id));
        assertEquals(0, gateway.addebiti());
    }

    @Test
    void multaPagataNonSiAnnulla() {
        UUID id = crea(servizi, clock, "AB123CD", 92);
        servizi.pay(id);

        assertThrows(IllegalStateException.class, () -> annullamento.cancel(id, "Ricorso"));
        assertEquals("PAID", servizi.getState(id).getState());
        assertEquals("PAID", statoDao(id));
        assertFalse(servizi.getCopy(id).getRuling().isPresent());
    }

    @Test
    void multaEmessaSiAnnullaEResteraAnnullata() {
        UUID id = crea(servizi, clock, "AB123CD", 92);
        annullamento.cancel(id, "Errore di rilevazione");
        assertEquals("CANCELLED", servizi.getState(id).getState());
        assertEquals("CANCELLED", statoDao(id));

        assertThrows(IllegalStateException.class, () -> servizi.pay(id));
        assertThrows(IllegalStateException.class, () -> annullamento.cancel(id, "Di nuovo"));
        assertEquals(0, gateway.addebiti());
    }

    @Test
    void scadenzaScattaSoloDopoLaData() {
        UUID id = crea(servizi, clock, "AB123CD", 92);

        servizi.checkExpiration(id);
        assertEquals("ISSUED", servizi.getState(id).getState());

        clock.advance(Duration.ofDays(60));   // l'ultimo giorno non è ancora scaduta
        servizi.checkExpiration(id);
        assertEquals("ISSUED", servizi.getState(id).getState());

        clock.advance(Duration.ofDays(1));
        servizi.checkExpiration(id);
        assertEquals("EXPIRED", servizi.getState(id).getState());
        assertEquals("EXPIRED", statoDao(id));
    }

    @Test
    void multaScadutaSiPagaEUnaPagataNonScade() {
        UUID scaduta = crea(servizi, clock, "AB123CD", 92);
        UUID pagata = crea(servizi, clock, "EF456GH", 81);
        servizi.pay(pagata);

        clock.advance(Duration.ofDays(90));
        servizi.checkExpiration(scaduta);
        servizi.checkExpiration(pagata);

        assertEquals("EXPIRED", servizi.getState(scaduta).getState());
        assertEquals("PAID", servizi.getState(pagata).getState());
        assertTrue(servizi.pay(scaduta));
        assertEquals("PAID", statoDao(scaduta));
    }

    @Test
    void multaScadutaSiPuoAnnullare() {
        UUID id = crea(servizi, clock, "AB123CD", 92);
        clock.advance(Duration.ofDays(61));
        servizi.checkExpiration(id);
        annullamento.cancel(id, "Ricorso accolto");
        assertEquals("CANCELLED", servizi.getState(id).getState());
    }

    @Test
    void intestatarioEIlProprietarioAlMomentoDellaRilevazione() {
        Instant t0 = clock.instant();
        clock.advance(Duration.ofDays(10));
        registro.assign("AB123CD", 2);                         // vendita al giorno 10

        // rilevata al giorno 5 ma arrivata solo ora, dopo la vendita: va al vecchio proprietario
        UUID tardiva = servizi.createFine(new Detection("A1", "AB123CD", 80, 50, t0.plus(Duration.ofDays(5))));
        UUID dopo = crea(servizi, clock, " ab123cd", 80);        // targa normalizzata

        assertEquals(1, servizi.getCopy(tardiva).getOwnerId());
        assertEquals(2, servizi.getCopy(dopo).getOwnerId());
        assertEquals("AB123CD", servizi.getCopy(dopo).getPlate());
        assertEquals(1, dao.findByOwner(1).size());
        assertEquals(1, dao.findByOwner(2).size());
    }

    @Test
    void rilevazioneAnterioreAllaRegistrazioneDellaTargaERifiutata() {
        Detection r = new Detection("A1", "AB123CD", 80, 50, clock.instant().minus(Duration.ofDays(1)));
        assertThrows(UnregisteredPlateException.class, () -> servizi.createFine(r));
    }

    @Test
    void senzaInfrazioneNonNasceUnaMulta() {
        assertThrows(IllegalArgumentException.class, () -> crea(servizi, clock, "AB123CD", 50));   // uguale al limite
        assertThrows(IllegalArgumentException.class, () -> crea(servizi, clock, "AB123CD", 30));
        assertThrows(IllegalArgumentException.class, () -> crea(servizi, clock, "AB123CD", 55));   // dentro il margine di misura
        assertTrue(dao.findByOwner(1).isEmpty());
        crea(servizi, clock, "AB123CD", 56);                                                       // oltre il margine
        assertEquals(1, dao.findByOwner(1).size());
    }

    @Test
    void limiteEImportoVengonoDallaRilevazione() {
        UUID id = servizi.createFine(new Detection("A7", "AB123CD", 92, 70, clock.instant()));
        Fine m = servizi.getCopy(id);
        assertEquals(37_000L, m.getAmountCents());           // 15000 + 22 km/h in eccesso * 1000
        assertEquals("A7", m.getDetection().getCameraId());
        assertEquals(70, m.getDetection().getLimit());
        assertEquals(92, m.getDetectedSpeed());
    }

    @Test
    void targaNonRegistrataERifiutata() {
        assertThrows(UnregisteredPlateException.class, () -> crea(servizi, clock, "ZZ000ZZ", 90));
    }

    @Test
    void multaInesistente() {
        assertThrows(IllegalArgumentException.class, () -> servizi.getState(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> servizi.pay(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> servizi.getCopy(UUID.randomUUID()));
    }
}
