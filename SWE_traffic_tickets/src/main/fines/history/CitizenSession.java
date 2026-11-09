package fines.history;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import fines.domain.Fine;
import fines.users.User;

/**
 * Ciò che si ottiene entrando con codice fiscale e password: le cose private di un cittadino
 * (vedere le proprie multe e pagarle). Chi ha anche una carica pubblica ottiene questo stesso oggetto,
 * come tutti: non ha scorciatoie verso i privilegi della carica, e con la carica non ha questi.
 * Il costruttore è package-private: la crea solo il LoginProxy.
 */
public final class CitizenSession {

    private final User user;
    private final FineHistory history;       // proxy legato a questo utente: può leggere solo le sue multe
    private final OnlinePayment payment;  // null se il LoginProxy non ha i servizi

    CitizenSession(User user, FineHistory history, OnlinePayment payment) {
        this.user = Objects.requireNonNull(user);
        this.history = Objects.requireNonNull(history);
        this.payment = payment;
    }

    public User getUser() { return user; }

    /** Le multe di chi ha fatto il login: non serve indicare nessun id. */
    public List<Fine> getMyFines() {
        return history.getFines(user.getId());
    }

    /**
     * Paga la multa con l'id scelto dalla lista (vedi OnlinePayment per la scelta sui controlli).
     *
     * @return true se pagata, false se il gestore dei pagamenti ha rifiutato
     * @throws IllegalStateException se la multa non è pagabile o la sessione non ha accesso ai servizi
     */
    public boolean pay(UUID fineId) {
        if (payment == null) {
            throw new IllegalStateException("Pagamento non disponibile in questa sessione");
        }
        return payment.pay(fineId);
    }
}
