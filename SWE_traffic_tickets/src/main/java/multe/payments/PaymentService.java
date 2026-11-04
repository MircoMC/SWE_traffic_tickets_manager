package fines.payments;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import fines.domain.PaymentGateway;

/**
 * Servizio di pagamento. Per ora è un finto: accetta sempre il pagamento.
 * Rispetta il contratto di PaymentGateway: è idempotente per fineId, quindi una multa
 * già addebitata non viene addebitata una seconda volta.
 * In seguito qui si parlerà con il gestore dei pagamenti reale, che dovrà verificare
 * lato server importo e idempotenza (questa classe è pubblica e non si può nascondere in Java).
 */
public final class PaymentService implements PaymentGateway {

    private final Set<UUID> charged = ConcurrentHashMap.newKeySet();

    /** @return true se la multa risulta addebitata */
    @Override
    public boolean payment(UUID fineId, long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Importo non valido: " + amountCents);
        }
        if (!charged.add(fineId)) {
            System.out.println("Multa " + fineId + " già addebitata: nessun nuovo addebito");
            return true;
        }
        System.out.println("Pagamento di " + amountCents + " cent per la multa " + fineId);
        return true;
    }
}
