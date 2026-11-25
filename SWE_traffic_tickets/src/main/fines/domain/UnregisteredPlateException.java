package fines.domain;

/** A violation was detected with a plate that is not assigned to any user. */
public class UnregisteredPlateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnregisteredPlateException(String plate) {
        super("Unregistered plate: " + plate);
    }
}
