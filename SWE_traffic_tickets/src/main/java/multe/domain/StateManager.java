package fines.domain;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sta tra Multa e FineState: è l'unico che cambia lo stato di una multa.
 * Lavora sempre per id e recupera l'oggetto originale dalla Identity Map (IdentityMap).
 *
 * Ogni operazione segue lo stesso schema: (1) lo stato corrente dice quale sarà il successivo
 * e rifiuta se l'operazione non è consentita, (2) si esegue l'azione esterna (addebito),
 * (3) si SALVA nel DAO la multa aggiornata, (4) solo se il salvataggio riesce si cambia la multa
 * in memoria e si avvisano gli osservatori. Così la memoria non è mai "più avanti" del database.
 *
 * Concorrenza: operazioni su multe diverse procedono in parallelo, su una stessa multa sono
 * serializzate dal monitor privato Multa.lock. Gli osservatori girano dentro il lock (ricevono
 * copie e gli errori sono isolati); se diventeranno lenti andranno spostati su una coda.
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

    FineState getState(int fineId) {
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            return fine.getState();
        }
    }

    /**
     * Avvia il pagamento: se il gestore risponde true la multa passa a Pagata.
     * Se il salvataggio fallisce DOPO l'addebito, l'eccezione arriva al chiamante e lo stato resta
     * invariato: ripetere paga() completa la transizione senza addebitare di nuovo
     * (il PaymentGateway è idempotente per id).
     *
     * @return true se pagata, false se il gestore ha rifiutato (stato invariato)
     * @throws IllegalStateException se nello stato attuale la multa non è pagabile
     */
    boolean paymentStarting(int fineId) {
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            FineState next = fine.getState().onPay();   // rifiuta PRIMA di addebitare
            if (!payments.payment(fine.getId(), fine.getAmountCents())) {
                return false;
            }
            apply(fine, next, FineEvent.PAID, null);
            return true;
        }
    }

    /** Annullamento disposto da un giudice. Una multa pagata non è annullabile. */
    void cancelStarting(int fineId, int judgeId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Motivazione obbligatoria");
        }
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            FineState next = fine.getState().onCancel();
            apply(fine, next, FineEvent.CANCELLED,
                new CancellationRuling(judgeId, reason.trim(), LocalDate.now(clock)));
        }
    }

    /** La data viene dal Clock del sistema, non dal chiamante. */
    void checkExpiration(int fineId) {
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

    /** Sempre dentro un synchronized(multa.lock). Prima il DAO, poi la memoria, poi gli osservatori. */
    private void apply(Fine fine, FineState next, FineEvent event, CancellationRuling ruling) {
        if (identityMap.get(fine.getId()) != fine) {
            throw new IllegalArgumentException(
                "La multa " + fine.getId() + " non è quella registrata nel IdentityMap");
        }
        fineDAO.update(fine.copyWith(next, ruling));
        fine.changeState(next, event, ruling);
    }

    private Fine find(int fineId) {
        Fine fine = identityMap.get(fineId);
        if (fine == null) {
            throw new IllegalArgumentException("Multa inesistente: " + fineId);
        }
        return fine;
    }
}
