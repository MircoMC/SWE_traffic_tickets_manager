package fines.testutil;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import fines.domain.PaymentGateway;

/** Gestore dei pagamenti finto: idempotente per id (come da contratto) e con il conto degli addebiti reali. */
public final class GatewayContatore implements PaymentGateway {

    private final Set<UUID> addebitate = ConcurrentHashMap.newKeySet();
    private final AtomicInteger addebiti = new AtomicInteger();
    private volatile boolean rifiuta;

    @Override
    public boolean payment(UUID idMulta, long amountCents) {
        if (rifiuta) {
            return false;
        }
        if (addebitate.add(idMulta)) {
            addebiti.incrementAndGet();
        }
        return true;
    }

    public int addebiti()            { return addebiti.get(); }
    public void rifiuta(boolean r)   { this.rifiuta = r; }
}
