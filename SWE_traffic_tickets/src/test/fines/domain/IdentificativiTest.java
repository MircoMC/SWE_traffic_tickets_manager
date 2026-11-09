package fines.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import fines.testutil.Infrazioni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentificativiTest {

    private final Clock clock = Clock.systemUTC();
    private InMemoryFineDAO dao;
    private FineService servizi;

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        PlateRegistry registro = new InMemoryPlateRegistry(clock);
        registro.assign("AB123CD", 1, clock.instant().minus(Duration.ofDays(30)));
        servizi = new FineService(dao, registro, new GatewayContatore(), clock);
    }

    @Test
    void gliIdSonoCasualiDistintiENonSequenziali() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ids.add(servizi.createFine(Infrazioni.di("AB123CD", 80, clock)));
        }
        assertEquals(500, new HashSet<>(ids).size());
        for (UUID id : ids) {
            assertEquals(4, id.version());            // UUID casuale (122 bit), non un contatore travestito
        }
        for (int i = 1; i < ids.size(); i++) {
            long salto = ids.get(i).getLeastSignificantBits() - ids.get(i - 1).getLeastSignificantBits();
            assertFalse(salto == 1);                  // da un id non si ricava il successivo
        }
    }

    @Test
    void ilNumeroDiMulteNonSiRicavaDaglIdSondando() {
        servizi.createFine(Infrazioni.di("AB123CD", 80, clock));
        // chi non conosce l'id riceve sempre la stessa risposta, esista o no una multa
        for (int i = 0; i < 100; i++) {
            UUID sondato = UUID.randomUUID();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> servizi.getState(sondato));
            assertTrue(e.getMessage().startsWith("Multa inesistente"));
        }
    }

    @Test
    void loStoricoSegueLOrdineDiRilevazioneNonQuelloDegliId() {
        Instant ora = clock.instant();
        servizi.createFine(new Detection("A1", "AB123CD", 80, 50, ora.minus(Duration.ofDays(3))));
        servizi.createFine(new Detection("A1", "AB123CD", 80, 50, ora.minus(Duration.ofDays(1))));
        servizi.createFine(new Detection("A1", "AB123CD", 80, 50, ora.minus(Duration.ofDays(2))));

        List<Fine> storico = dao.findByOwner(1);
        assertEquals(3, storico.size());
        assertEquals(ora.minus(Duration.ofDays(3)), storico.get(0).getDetection().getDetectedAt());
        assertEquals(ora.minus(Duration.ofDays(2)), storico.get(1).getDetection().getDetectedAt());
        assertEquals(ora.minus(Duration.ofDays(1)), storico.get(2).getDetection().getDetectedAt());
    }
}
