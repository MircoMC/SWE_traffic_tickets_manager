package fines.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sta tra Fine e FineState: è l'unico che cambia lo stato di una multa.
 * Lavora sempre per id e recupera l'oggetto originale dalla Identity Map (IdentityMap).
 *
 * Ogni operazione segue lo stesso schema: (1) lo stato corrente dice quale sarà il successivo
 * e rifiuta se l'operazione non è consentita, (2) si esegue l'azione esterna (addebito),
 * (3) si SALVA nel DAO la multa aggiornata, (4) solo se il salvataggio riesce si cambia la multa
 * in memoria e si avvisano gli osservatori. Così la memoria non è mai "più avanti" del database.
 *
 * Concorrenza: operazioni su multe diverse procedono in parallelo, su una stessa multa sono
 * serializzate dal monitor privato Fine.lock. Gli osservatori girano dentro il lock (ricevono
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

    FineState getState(UUID fineId) {
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            return fine.getState();
        }
    }

    /**
     * Avvia il pagamento: se il gestore risponde true la multa passa a Paid.
     * Se il salvataggio fallisce DOPO l'addebito, l'eccezione arriva al chiamante e lo stato resta
     * invariato: ripetere pay() completa la transizione senza addebitare di nuovo
     * (il PaymentGateway è idempotente per id).
     * LIMITE NOTO: se prima di un nuovo tentativo un giudice annulla la multa, l'addebito resta; in produzione
     * si riconcilia periodicamente con il gestore dei pagamenti.
     *
     * @return true se pagata, false se il gestore ha rifiutato (stato invariato)
     * @throws IllegalStateException se nello stato attuale la multa non è pagabile
     */
    boolean paymentStarting(UUID fineId) {
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

    /** Cancellation disposto da un giudice. Una multa pagata non è annullabile. */
    void cancelStarting(UUID fineId, int judgeId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Motivazione obbligatoria");
        }
        Fine fine = find(fineId);
        synchronized (fine.lock) {
            // conflitto di interessi: un giudice non decide sulle multe intestate a se stesso
            // (l'id della persona è lo stesso per l'account privato e per la carica)
            if (fine.getOwnerId() == judgeId) {
                throw new SecurityException("Il giudice non può annullare una multa a lui intestata");
            }
            FineState next = fine.getState().onCancel();
            apply(fine, next, FineEvent.CANCELLED,
                new CancellationRuling(judgeId, reason.trim(), LocalDate.now(clock)));
        }
    }

    /** La data viene dal Clock del sistema, non dal chiamante. */
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

    /** Sempre dentro un synchronized(multa.lock). Prima il DAO, poi la memoria, poi gli osservatori. */
    private void apply(Fine fine, FineState next, FineEvent event, CancellationRuling ruling) {
        if (identityMap.get(fine.getId()) != fine) {
            throw new IllegalArgumentException(
                "La multa " + fine.getId() + " non è quella registrata nell'IdentityMap");
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
