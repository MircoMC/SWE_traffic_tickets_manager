package multe.dominio;

/** Rilevata un'infrazione con una targa che non risulta assegnata a nessun utente. */
public class TargaNonRegistrataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TargaNonRegistrataException(String targa) {
        super("Targa non registrata: " + targa);
    }
}
