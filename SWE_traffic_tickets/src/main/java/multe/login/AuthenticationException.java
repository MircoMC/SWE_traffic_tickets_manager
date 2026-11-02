package fines.login;

/** Login fallito. Il messaggio è volutamente generico: non dice se è sbagliato l'utente o la password. */
public class AuthenticationException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
