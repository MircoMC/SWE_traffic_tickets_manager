package fines.speedcamera;

/**
 * Per questa misura non si conosce la configurazione dell'autovelox (autovelox sconosciuto, o misura anteriore
 * al primo periodo). Non si indovina un limite: la misura non viene giudicata e si può riprovare.
 */
public class ConfigurationUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigurationUnavailableException(String message) {
        super(message);
    }
}
