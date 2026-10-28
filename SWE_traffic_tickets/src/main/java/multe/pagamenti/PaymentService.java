package multe.pagamenti;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import multe.dominio.GatewayPagamento;

/**
 * Servizio di pagamento. Per ora è un finto: accetta sempre il pagamento.
 * Rispetta il contratto di GatewayPagamento: è idempotente per idMulta, quindi una multa
 * già addebitata non viene addebitata una seconda volta.
 * In seguito qui si parlerà con il gestore dei pagamenti reale, che dovrà verificare
 * lato server importo e idempotenza (questa classe è pubblica e non si può nascondere in Java).
 */
public final class PaymentService implements GatewayPagamento {

    private final Set<Integer> addebitate = ConcurrentHashMap.newKeySet();

    /** @return true se la multa risulta addebitata */
    @Override
    public boolean payment(int idMulta, long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Importo non valido: " + amountCents);
        }
        if (!addebitate.add(idMulta)) {
            System.out.println("Multa " + idMulta + " già addebitata: nessun nuovo addebito");
            return true;
        }
        System.out.println("Pagamento di " + amountCents + " cent per la multa " + idMulta);
        return true;
    }
}
