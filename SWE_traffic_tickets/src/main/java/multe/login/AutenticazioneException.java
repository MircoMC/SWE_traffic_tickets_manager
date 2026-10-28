package multe.login;

/** Login fallito. Il messaggio è volutamente generico: non dice se è sbagliato l'utente o la password. */
public class AutenticazioneException extends Exception {

    private static final long serialVersionUID = 1L;

    public AutenticazioneException(String message) {
        super(message);
    }
}
