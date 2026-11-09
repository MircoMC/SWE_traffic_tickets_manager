package fines.login;

/**
 * Il dispositivo ha fatto troppi tentativi di accesso falliti ed è bloccato per un po'.
 * Il blocco riguarda solo il dispositivo (mai un account): dirlo non rivela nulla su nessun account.
 * La password non è stata nemmeno controllata.
 */
public class TooManyAttemptsException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    public TooManyAttemptsException() {
        super("Troppi tentativi effettuati, ritenta più tardi");
    }
}
