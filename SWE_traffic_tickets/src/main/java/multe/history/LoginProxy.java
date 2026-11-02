package fines.history;

import fines.domain.Cancellation;
import fines.domain.FineService;
import fines.login.AuthenticationException;
import fines.login.LoginGateway;
import fines.login.Identity;
import fines.users.Administrator;
import fines.users.PoliceOfficer;
import fines.users.Citizen;
import fines.users.Judge;
import fines.users.User;

import java.util.Arrays;
import java.util.Objects;

/**
 * Punto di ingresso per i client: autentica l'utente tramite LoginGateway e gli consegna
 * una Sessione con ciò che il suo ruolo consente: uno FineHistoryProxy legato alla sua identità e,
 * solo per i giudici, l'Annullamento.
 * L'identità non è mai un oggetto Utente fornito dal chiamante: nasce qui dal ruolo salvato nel database.
 * Il codice di assemblaggio può comunque costruire uno FullFineHistory (vedi PlatesDemo):
 * la protezione vale per chi riceve solo la Sessione.
 */
public class LoginProxy {

    private final LoginGateway gateway;
    private final FineHistory realHistory;
    private final FineService services;   // può essere null: allora nessuno ottiene l'Annullamento

    public LoginProxy(LoginGateway gateway, FineHistory realHistory) {
        this(gateway, realHistory, null);
    }

    public LoginProxy(LoginGateway gateway, FineHistory realHistory, FineService services) {
        this.gateway = Objects.requireNonNull(gateway);
        this.realHistory = Objects.requireNonNull(realHistory);
        this.services = services;
    }

    /**
     * La password viene azzerata prima di uscire, anche in caso di errore.
     *
     * @throws AuthenticationException se username o password non sono corretti
     */
    public Session openSession(String username, char[] password) throws AuthenticationException {
        try {
            Identity identity = gateway.authenticate(username, password)
                .orElseThrow(() -> new AuthenticationException("Credenziali non valide"));
            User user = createUser(identity);
            Cancellation cancellation = (user instanceof Judge && services != null)
                ? services.cancellationFor((Judge) user) : null;
            return new Session(user, new FineHistoryProxy(user, realHistory), cancellation);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Scorciatoia per chi serve solo lo storico. */
    public FineHistory login(String username, char[] password) throws AuthenticationException {
        return openSession(username, password).getHistory();
    }

    /** Unico punto in cui il ruolo salvato diventa il tipo di Utente (e quindi i suoi privilegi). */
    private User createUser(Identity identity) {
        switch (identity.getRole()) {
            case CITIZEN:      return new Citizen(identity.getUserId(), identity.getUsername());
            case POLICE_OFFICER:    return new PoliceOfficer(identity.getUserId(), identity.getUsername());
            case ADMINISTRATOR: return new Administrator(identity.getUserId(), identity.getUsername());
            case JUDGE:        return new Judge(identity.getUserId(), identity.getUsername());
            default:
                throw new IllegalStateException("Ruolo sconosciuto: " + identity.getRole());
        }
    }
}
