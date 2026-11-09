package fines.history;

import java.util.Objects;
import java.util.Optional;
import fines.speedcamera.SpeedCameraManagement;
import fines.domain.Cancellation;
import fines.users.User;

/**
 * Ciò che si ottiene entrando con la carica pubblica (codice fiscale, codice identificativo e password):
 * i privilegi del ruolo. Non ha né il pagamento né "le mie multe": le cose private si fanno solo
 * entrando da cittadino. Il costruttore è package-private: la crea solo il LoginProxy.
 */
public final class OfficeSession {

    private final User user;
    private final FineHistory history;
    private final Cancellation cancellation;   // null se la carica non è quella di giudice
    private final SpeedCameraManagement management;  // null se la carica non è quella di amministratore

    OfficeSession(User user, FineHistory history, Cancellation cancellation, SpeedCameraManagement management) {
        this.user = Objects.requireNonNull(user);
        this.history = Objects.requireNonNull(history);
        this.cancellation = cancellation;
        this.management = management;
    }

    public User getUser()        { return user; }

    /** Le forze dell'ordine e i giudici possono consultare le multe di chiunque, indicando l'id dell'utente. */
    public FineHistory getHistory() { return history; }

    /** Presente solo per i giudici (e solo se il LoginProxy ha i servizi). */
    public Optional<Cancellation> getCancellation() {
        return Optional.ofNullable(cancellation);
    }

    /** Presente solo per gli amministratori (e solo se il LoginProxy ha i servizi): programma i cambi di configurazione. */
    public Optional<SpeedCameraManagement> getSpeedCameraManagement() {
        return Optional.ofNullable(management);
    }
}
