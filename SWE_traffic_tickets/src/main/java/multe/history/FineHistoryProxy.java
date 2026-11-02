package fines.history;

import fines.domain.Fine;
import fines.users.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Proxy di protezione: nasce al login ed è legato all'utente che ha inserito le credenziali.
 * Prima di passare la richiesta allo storico reale controlla che QUELL'utente ne abbia il diritto.
 * L'utente non arriva mai dal chiamante: il solo dato che il chiamante fornisce è l'id da consultare.
 */
public class FineHistoryProxy implements FineHistory {

    private final User requester;
    private final FineHistory real;

    FineHistoryProxy(User requester, FineHistory real) {   // lo crea solo il LoginProxy
        this.requester = Objects.requireNonNull(requester);
        this.real = Objects.requireNonNull(real);
    }

    @Override
    public List<Fine> getFines(int userId) {
        if (!requester.canViewFinesOf(userId)) {
            throw new SecurityException("L'utente " + requester.getId()
                + " non può consultare le multe dell'utente " + userId);
        }
        // lista non modificabile: chi la riceve non può alterare lo storico
        return Collections.unmodifiableList(new ArrayList<>(real.getFines(userId)));
    }
}
