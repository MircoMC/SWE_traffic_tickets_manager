package fines.login;

import java.util.Objects;

/** Esito di un'autenticazione riuscita: chi è l'utente e con quale ruolo. Non contiene mai password o hash. */
public final class Identity {

    private final int userId;
    private final String username;
    private final Role role;

    public Identity(int userId, String username, Role role) {
        this.userId = userId;
        this.username = Objects.requireNonNull(username);
        this.role = Objects.requireNonNull(role);
    }

    public int getUserId()   { return userId; }
    public String getUsername() { return username; }
    public Role getRole()    { return role; }
}
