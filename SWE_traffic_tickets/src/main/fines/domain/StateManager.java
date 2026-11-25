package fines.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sits between Fine and FineState: it is the only one that changes the state of a fine.
 * It always works by id and gets the original object from the Identity Map (IdentityMap).
 *
 * Every operation follows the same scheme: (1) the current state says which one comes next
 * and rejects if the operation is not allowed, (2) the external action is performed (charge),
 * (3) the updated fine is SAVED in the DAO, (4) only if saving succeeds the fine is changed
 * in memory and the observers are notified. So memory is never "ahead" of the database.
 *
 * Concurrency: operations on different fines proceed in parallel, on the same fine they are
 * serialized by the private monitor Fine.lock. Observers run inside the lock (they receive
 * copies and errors are isolated); if they become slow they should be moved to a queue.
 */
class StateManager {

    private final IdentityMap identityMap;
    private final PaymentGateway payments;
    private final FineDAO fineDAO;
    private final Clock clock;

    StateManager(IdentityMap identityMap, PaymentGateway payments, FineDAO fineDAO, Clock clock) {
        this.identityMap = identityMap;
        this.payments = payments;
        this.fineDAO = fineDAO;
        this.clock = clock;
    }

    FineState getState(UUID fineId) {
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            return fine.getState();
        }
    }

    /**
     * Starts the payment: if the provider answers true the fine becomes Paid.
     * If saving fails AFTER the charge, the exception reaches the caller and the state stays
     * unchanged: repeating pay() completes the transition without charging again
     * (the PaymentGateway is idempotent per id).
     *
     * @return true if paid, false if the provider refused (state unchanged)
     * @throws IllegalStateException if in the current state the fine cannot be paid
     */
    boolean paymentStarting(UUID fineId) {
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            FineState next = fine.getState().onPay();   // rejects BEFORE charging
            if (!payments.payment(fine.getId(), fine.getAmountCents())) {
                return false;
            }
            apply(fine, next, FineEvent.PAID, null);
            return true;
        }
    }

    /** Cancellation ordered by a judge. A paid fine cannot be cancelled. */
    void cancelStarting(UUID fineId, int judgeId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required");
        }
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            if (fine.getOwnerId() == judgeId) {
                throw new SecurityException("Il giudice non può annullare una multa a lui intestata");
            }
            FineState next = fine.getState().onCancel();
            apply(fine, next, FineEvent.CANCELLED,
                new CancellationRuling(judgeId, reason.trim(), LocalDate.now(clock)));
        }
    }

    /** The date comes from the system Clock, not from the caller. */
    void checkExpiration(UUID fineId) {
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            if (LocalDate.now(clock).isAfter(fine.getDueDate())) {
                FineState current = fine.getState();
                FineState next = current.onExpire();
                if (next != current) {
                    apply(fine, next, FineEvent.EXPIRED, null);
                }
            }
        }
    }

    /** Always inside a synchronized(fine.lock). First the DAO, then memory, then the observers. */
    private void apply(Fine fine, FineState next, FineEvent event, CancellationRuling ruling) {
        if (identityMap.get(fine.getId()) != fine) {
            throw new IllegalArgumentException(
                "Fine " + fine.getId() + " is not the one registered in the IdentityMap");
        }
        fineDAO.update(fine.copyWith(next, ruling));
        fine.changeState(next, event, ruling);
    }

    private Fine find(UUID fineId) {
        Fine fine = identityMap.get(fineId);
        if (fine == null) {
            throw new IllegalArgumentException("Multa inesistente: " + fineId);
        }
        return fine;
    }
}
