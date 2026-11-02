package fines.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fines.app.AdjustableClock;
import fines.payments.PaymentService;
import fines.persistence.InMemoryFineDAO;
import fines.plates.InMemoryPlateRegistry;
import fines.plates.PlateRegistry;
import fines.users.Judge;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FineServiceTest {

    private AdjustableClock clock;
    private FineDAO dao;
    private PlateRegistry registry;
    private FineService service;
    private int fineId;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        dao = new InMemoryFineDAO();
        registry = new InMemoryPlateRegistry(clock);
        registry.assign("AB123CD", 1, clock.instant().minus(Duration.ofDays(30)));
        service = new FineService(dao, registry, new PaymentService(), clock);
        fineId = service.createFine(new Detection("A1", "AB123CD", 92, 70, clock.instant()));
    }

    private String state(int id) {
        return service.getState(id).getState();
    }

    @Test
    void newFineIsIssuedAndTheAmountDependsOnTheExcess() {
        assertEquals("ISSUED", state(fineId));
        assertEquals(15_000L + 22 * 1_000L, service.getCopy(fineId).getAmountCents());
        assertEquals(1, service.getCopy(fineId).getOwnerId());
    }

    @Test
    void payingMovesToPaidAndTheDaoFollows() {
        assertTrue(service.pay(fineId));
        assertEquals("PAID", state(fineId));
        assertEquals("PAID", dao.findById(fineId).get().getState().getState());
    }

    @Test
    void payingTwiceIsRejected() {
        service.pay(fineId);
        assertThrows(IllegalStateException.class, () -> service.pay(fineId));
    }

    @Test
    void expiredFineCanStillBePaid() {
        clock.advance(Duration.ofDays(61));
        service.checkExpiration(fineId);
        assertEquals("EXPIRED", state(fineId));
        assertTrue(service.pay(fineId));
        assertEquals("PAID", state(fineId));
    }

    @Test
    void expirationHasNoEffectOnPaidFines() {
        service.pay(fineId);
        clock.advance(Duration.ofDays(61));
        service.checkExpiration(fineId);
        assertEquals("PAID", state(fineId));
    }

    @Test
    void paidFineCannotBeCancelled() {
        service.pay(fineId);
        Cancellation cancellation = service.cancellationFor(new Judge(10, "judge"));
        assertThrows(IllegalStateException.class, () -> cancellation.cancel(fineId, "Appeal"));
    }

    @Test
    void cancelledFineKeepsTheRulingAndCannotBePaid() {
        service.cancellationFor(new Judge(10, "judge")).cancel(fineId, "Detection error");
        assertEquals("CANCELLED", state(fineId));
        CancellationRuling ruling = service.getCopy(fineId).getRuling().get();
        assertEquals(10, ruling.getJudgeId());
        assertEquals("Detection error", ruling.getReason());
        assertThrows(IllegalStateException.class, () -> service.pay(fineId));
    }

    @Test
    void cancellationNeedsAReason() {
        Cancellation cancellation = service.cancellationFor(new Judge(10, "judge"));
        assertThrows(IllegalArgumentException.class, () -> cancellation.cancel(fineId, "  "));
        assertEquals("ISSUED", state(fineId));
    }

    @Test
    void aMeasureWithinTheMarginDoesNotCreateAFine() {
        assertThrows(IllegalArgumentException.class,
            () -> service.createFine(new Detection("A1", "AB123CD", 72, 70, clock.instant())));
    }

    @Test
    void unknownPlateIsRejected() {
        assertThrows(UnregisteredPlateException.class,
            () -> service.createFine(new Detection("A1", "ZZ000ZZ", 90, 50, clock.instant())));
    }

    @Test
    void unknownFineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.getCopy(999));
        assertThrows(IllegalArgumentException.class, () -> service.pay(999));
    }

    @Test
    void lateDetectionGoesToTheOwnerAtMeasureTime() {
        registry.assign("EF456GH", 1, clock.instant().minus(Duration.ofDays(365)));
        registry.assign("EF456GH", 2, clock.instant().minus(Duration.ofDays(1)));   // sold yesterday
        int late = service.createFine(
            new Detection("A1", "EF456GH", 90, 50, clock.instant().minus(Duration.ofDays(2))));
        int recent = service.createFine(new Detection("A1", "EF456GH", 90, 50, clock.instant()));
        assertEquals(1, service.getCopy(late).getOwnerId());
        assertEquals(2, service.getCopy(recent).getOwnerId());
    }

    @Test
    void getCopyReturnsIndependentObjects() {
        assertNotSame(service.getCopy(fineId), service.getCopy(fineId));
    }

    @Test
    void observersReceiveEventsEvenIfAnotherObserverFails() {
        List<FineEvent> received = new ArrayList<>();
        service.addObserver((source, arg) -> { throw new RuntimeException("broken observer"); });
        service.addObserver(new FineObserver() {
            @Override
            protected void onEvent(Fine fine, FineEvent event) {
                received.add(event);
            }
        });
        int id = service.createFine(new Detection("A1", "AB123CD", 90, 50, clock.instant()));
        assertDoesNotThrow(() -> service.pay(id));
        assertEquals(List.of(FineEvent.ISSUED, FineEvent.PAID), received);
    }
}
