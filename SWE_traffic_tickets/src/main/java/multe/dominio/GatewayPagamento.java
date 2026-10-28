package multe.dominio;

/**
 * Porta verso il gestore dei pagamenti, definita dal dominio e implementata fuori.
 *
 * CONTRATTO: {@code payment} deve essere IDEMPOTENTE per idMulta. Se per quell'id l'addebito
 * è già avvenuto, deve restituire true senza addebitare di nuovo. Serve perché, se il salvataggio
 * fallisce dopo l'addebito, il chiamante può ripetere paga() senza far pagare due volte.
 */
public interface GatewayPagamento {

    /** @return true se la multa risulta addebitata (ora o in precedenza), false se il pagamento è rifiutato */
    boolean payment(int idMulta, long amountCents);
}
