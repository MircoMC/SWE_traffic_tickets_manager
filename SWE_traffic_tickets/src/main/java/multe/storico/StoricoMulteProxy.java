package multe.storico;

import multe.dominio.Multa;
import multe.utenti.Utente;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Proxy di protezione: nasce al login ed è legato all'utente che ha inserito le credenziali.
 * Prima di passare la richiesta allo storico reale controlla che QUELL'utente ne abbia il diritto.
 * L'utente non arriva mai dal chiamante: il solo dato che il chiamante fornisce è l'id da consultare.
 */
public class StoricoMulteProxy implements StoricoMulte {

    private final Utente richiedente;
    private final StoricoMulte reale;

    StoricoMulteProxy(Utente richiedente, StoricoMulte reale) {   // lo crea solo il LoginProxy
        this.richiedente = Objects.requireNonNull(richiedente);
        this.reale = Objects.requireNonNull(reale);
    }

    @Override
    public List<Multa> getMulte(int idUtente) {
        if (!richiedente.puoConsultareMulteDi(idUtente)) {
            throw new SecurityException("L'utente " + richiedente.getId()
                + " non può consultare le multe dell'utente " + idUtente);
        }
        // lista non modificabile: chi la riceve non può alterare lo storico
        return Collections.unmodifiableList(new ArrayList<>(reale.getMulte(idUtente)));
    }
}
