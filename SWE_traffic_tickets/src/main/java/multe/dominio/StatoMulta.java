package multe.dominio;

/**
 * State. Classe astratta con costruttore e metodi di transizione package-private:
 * da fuori il package si può solo leggere il nome dello stato, nessuno può
 * implementarne uno nuovo né invocare le transizioni.
 *
 * Gli stati sono funzioni pure: dicono QUALE sarà lo stato successivo, ma non
 * modificano nulla. Solo lo StateManager esegue la transizione, quindi
 * non si arriva a PAGATA se non passando da StateManager.paymentStarting.
 *
 * Di default pay e cancel sono rifiutate, expire non ha effetto
 * (una multa già pagata o annullata può restare oltre la scadenza senza errori).
 */
public abstract class StatoMulta {

    StatoMulta() {}

    public abstract String getState();

    /** @return lo stato dopo il pagamento; lancia IllegalStateException se non consentito */
    StatoMulta onPay() {
        throw rifiutata("pay");
    }

    /** @return lo stato dopo l'annullamento; lancia IllegalStateException se non consentito */
    StatoMulta onCancel() {
        throw rifiutata("cancel");
    }

    /** @return lo stato dopo la scadenza; this se la scadenza non ha effetto */
    StatoMulta onExpire() {
        return this;
    }

    private IllegalStateException rifiutata(String operazione) {
        return new IllegalStateException("Operation '" + operazione + "' not allowed in state: " + getState());
    }

    @Override
    public String toString() {
        return getState();
    }
}
