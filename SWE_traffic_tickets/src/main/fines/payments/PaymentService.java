package fines.payments;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import fines.domain.PaymentGateway;

/**
 * Payment service. For now it is a fake: it always accepts the payment.
 * It honors the PaymentGateway contract: it is idempotent per fineId, so a fine
 * already charged is not charged a second time.
 * Later this will talk to the real payment provider, which must verify
 * amount and idempotency server-side (this class is public and cannot be hidden in Java).
 */
public final class PaymentService implements PaymentGateway {

    private final Set<UUID> charged = ConcurrentHashMap.newKeySet();

    /** @return true if the fine is charged */
    @Override
    public boolean payment(UUID fineId, long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Invalid amount: " + amountCents);
        }
        if (!charged.add(fineId)) {
            System.out.println("Addebito già effettuato: nessun nuovo addebito");
            return true;
        }
        System.out.println("Pagamento di " + amountCents + " cent");
        return true;
    }
}
