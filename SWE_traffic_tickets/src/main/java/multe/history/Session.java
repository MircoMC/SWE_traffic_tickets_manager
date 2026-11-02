package fines.history;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import fines.domain.Cancellation;
import fines.domain.Fine;
import fines.users.User;

/**
 * Ciò che un utente ottiene dal login: solo gli strumenti che il suo ruolo gli consente.
 * Il costruttore è package-private: la crea solo il LoginProxy.
 */
public final class Session {

    private final User user;
    private final FineHistory history;
    private final Cancellation cancellation;   // null se l'utente non è un giudice

    Session(User user, FineHistory history, Cancellation cancellation) {
        this.user = Objects.requireNonNull(user);
        this.history = Objects.requireNonNull(history);
        this.cancellation = cancellation;
    }

    public User getUser()    { return user; }
    public FineHistory getHistory() { return history; }

    /** Le multe di chi ha fatto il login: non serve indicare nessun id. */
    public List<Fine> getMyFines() {
        return history.getFines(user.getId());
    }

    /** Presente solo per i giudici. */
    public Optional<Cancellation> getCancellation() {
        return Optional.ofNullable(cancellation);
    }
}
