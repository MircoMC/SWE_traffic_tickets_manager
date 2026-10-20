/**
 * State: ogni stato decide cosa succede alle operazioni sulla multa.
 * Le transizioni non toccano più la Multa direttamente: passano dallo StateManager,
 * che è l'unico punto autorizzato a cambiare lo stato.
 *
 * Di default pay e cancel sono rifiutate, expire non ha effetto
 * (una multa già pagata o annullata può restare oltre la scadenza senza errori).
 * Ogni stato ridefinisce solo ciò che permette.
 */
public interface StatoMulta {

    String getState();

    /** True se in questo stato la multa può essere pagata (serve allo StateManager prima di addebitare). */
    default boolean isPayable() {
        return false;
    }

    default void pay(StateManager manager, Multa multa) {
        throw new IllegalStateException("Operation 'pay' not allowed in state: " + getState());
    }

    default void cancel(StateManager manager, Multa multa) {
        throw new IllegalStateException("Operation 'cancel' not allowed in state: " + getState());
    }

    default void expire(StateManager manager, Multa multa) {
        // nessun effetto
    }
}
