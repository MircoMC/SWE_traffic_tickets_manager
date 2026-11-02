package fines.domain;

/** Rilevata un'infrazione con una targa che non risulta assegnata a nessun utente. */
public class UnregisteredPlateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnregisteredPlateException(String plate) {
        super("Targa non registrata: " + plate);
    }
}
