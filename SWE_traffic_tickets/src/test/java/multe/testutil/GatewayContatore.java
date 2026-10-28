package multe.testutil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import multe.dominio.GatewayPagamento;

/** Gestore dei pagamenti finto: idempotente per id (come da contratto) e con il conto degli addebiti reali. */
public final class GatewayContatore implements GatewayPagamento {

    private final Set<Integer> addebitate = ConcurrentHashMap.newKeySet();
    private final AtomicInteger addebiti = new AtomicInteger();
    private volatile boolean rifiuta;

    @Override
    public boolean payment(int idMulta, long amountCents) {
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
