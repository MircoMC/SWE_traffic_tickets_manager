package multe.login;

import java.util.Objects;

/** Esito di un'autenticazione riuscita: chi è l'utente e con quale ruolo. Non contiene mai password o hash. */
public final class Identita {

    private final int idUtente;
    private final String username;
    private final Ruolo ruolo;

    public Identita(int idUtente, String username, Ruolo ruolo) {
        this.idUtente = idUtente;
        this.username = Objects.requireNonNull(username);
        this.ruolo = Objects.requireNonNull(ruolo);
    }

    public int getIdUtente()   { return idUtente; }
    public String getUsername() { return username; }
    public Ruolo getRuolo()    { return ruolo; }
}
