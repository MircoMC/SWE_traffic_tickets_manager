package fines.history;

import fines.speedcamera.SpeedCameraManagement;
import fines.speedcamera.SpeedCameraServices;
import fines.domain.Cancellation;
import fines.domain.FineService;
import fines.login.AccessAlert;
import fines.login.AccountMonitor;
import fines.login.AttemptLimiter;
import fines.login.AuthenticationException;
import fines.login.Identity;
import fines.login.LoginGateway;
import fines.login.TooManyAttemptsException;
import fines.users.Administrator;
import fines.users.PoliceOfficer;
import fines.users.Citizen;
import fines.users.Judge;
import fines.users.User;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Punto di ingresso per i client. Due porte:
 * <ul>
 *   <li>{@link #openSession}: codice fiscale + password, restituisce una CitizenSession (le cose private);</li>
 *   <li>{@link #openOfficeSession}: codice fiscale + password + codice identificativo, restituisce una
 *       OfficeSession (i privilegi del ruolo: storico di tutti, per il giudice la Cancellation, per l'amministratore
 *       la SpeedCameraManagement).</li>
 * </ul>
 * Chi ha una carica entra da cittadino come tutti per le cose private, e con la carica per quelle pubbliche:
 * non può farle insieme. L'identità non è mai un oggetto User fornito dal chiamante: nasce qui dal ruolo
 * salvato nel database. Il codice di assemblaggio può comunque costruire uno FullFineHistory (vedi PlatesDemo):
 * la protezione vale per chi riceve solo la sessione.
 *
 * TENTATIVI FALLITI, due difese diverse:
 * <ul>
 *   <li>per DISPOSITIVO (soglia 3): superata la soglia il dispositivo è BLOCCATO e riceve un errore distinto,
 *       {@link TooManyAttemptsException}, senza che la password venga nemmeno controllata. Il blocco cresce a ogni
 *       errore successivo e il conteggio si azzera dopo 24 ore dal primo errore (vedi AttemptLimiter). Il tentativo
 *       si prenota in modo atomico PRIMA di controllare la password, quindi i tentativi lanciati in parallelo non
 *       aggirano la soglia; un accesso riuscito restituisce il tentativo prenotato (non ricarica gli altri). Dire che
 *       il dispositivo è bloccato non rivela nulla su nessun account, perché il blocco non dipende dall'account.</li>
 *   <li>per ACCOUNT (soglia 10): NESSUN blocco, perché chi conosce un codice fiscale potrebbe tenere fuori il titolare.
 *       Si avvisa invece il titolare (email) una volta sola per finestra, su un thread dedicato: gli accessi non
 *       rallentano e la durata non cambia se l'account esiste o no (vedi AccountMonitor).</li>
 * </ul>
 * Il dispositivo è un identificativo deciso dal server (IP o id emesso dal server), non dichiarato dal client.
 * SENZA dispositivo non c'è nessun blocco: i metodi senza il parametro servono a test e demo, un server vero
 * deve sempre passarlo.
 *
 * LIMITI NOTI: le sessioni non scadono e non si revocano (una Cancellation già consegnata resta valida finché
 * c'è la sessione); la protezione sui privilegi si basa sulla visibilità package-private, non su moduli Java;
 * un attacco distribuito (pochi tentativi da molti dispositivi diversi) non viene fermato, solo segnalato al titolare.
 */
public class LoginProxy {

    private static final String MESSAGE = "Credenziali non valide";

    private final LoginGateway gateway;
    private final FineHistory realHistory;
    private final FineService services;   // può essere null: allora nessuno ottiene Cancellation e pagamento
    private final SpeedCameraServices speedCameraServices;   // può essere null: allora nessuno ottiene la SpeedCameraManagement
    private final AttemptLimiter perDevice;
    private final AccountMonitor accountMonitor;
    private final AccessAlert alert;

    /** Un solo thread: l'avviso non rallenta gli accessi e non ne cambia i tempi. */
    private final ExecutorService alerts = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "avvisi-accessi");
        t.setDaemon(true);
        return t;
    });

    public LoginProxy(LoginGateway gateway, FineHistory realHistory) {
        this(gateway, realHistory, null);
    }

    public LoginProxy(LoginGateway gateway, FineHistory realHistory, FineService services) {
        this(gateway, realHistory, services, null);
    }

    public LoginProxy(LoginGateway gateway, FineHistory realHistory, FineService services,
                      SpeedCameraServices speedCameraServices) {
        this(gateway, realHistory, services, speedCameraServices, Clock.systemUTC());
    }

    public LoginProxy(LoginGateway gateway, FineHistory realHistory, FineService services,
                      SpeedCameraServices speedCameraServices, Clock clock) {
        this(gateway, realHistory, services, speedCameraServices, clock, AccessAlert.NONE);
    }

    public LoginProxy(LoginGateway gateway, FineHistory realHistory, FineService services,
                      SpeedCameraServices speedCameraServices, Clock clock, AccessAlert alert) {
        this(gateway, realHistory, services, speedCameraServices,
            new AttemptLimiter(clock), new AccountMonitor(clock), alert);
    }

    public LoginProxy(LoginGateway gateway, FineHistory realHistory, FineService services,
                      SpeedCameraServices speedCameraServices, AttemptLimiter perDevice,
                      AccountMonitor accountMonitor, AccessAlert alert) {
        this.gateway = Objects.requireNonNull(gateway);
        this.realHistory = Objects.requireNonNull(realHistory);
        this.services = services;
        this.speedCameraServices = speedCameraServices;
        this.perDevice = Objects.requireNonNull(perDevice);
        this.accountMonitor = Objects.requireNonNull(accountMonitor);
        this.alert = Objects.requireNonNull(alert);
    }

    /** Accesso da cittadino, senza dispositivo: nessun blocco (vedi la nota della classe). */
    public CitizenSession openSession(String username, char[] password) throws AuthenticationException {
        return openSession(username, password, null);
    }

    /**
     * Accesso da cittadino. La password viene azzerata prima di uscire, anche in caso di errore.
     *
     * @param device identificativo del dispositivo deciso dal server (null se non c'è)
     * @throws TooManyAttemptsException se il dispositivo è bloccato per i troppi tentativi
     * @throws AuthenticationException  se le credenziali non sono corrette
     */
    public CitizenSession openSession(String username, char[] password, String device)
            throws AuthenticationException {
        try {
            Identity identity = authenticate(username, false, device, password,
                () -> gateway.authenticate(username, password));
            User user = new Citizen(identity.getUserId(), identity.getUsername());
            OnlinePayment payment = (services != null) ? new OnlinePayment(services) : null;
            return new CitizenSession(user, new FineHistoryProxy(user, realHistory), payment);
        } finally {
            zeroOut(password);
        }
    }

    /** Accesso con la carica, senza dispositivo: nessun blocco (vedi la nota della classe). */
    public OfficeSession openOfficeSession(String username, String officeCode, char[] password)
            throws AuthenticationException {
        return openOfficeSession(username, officeCode, password, null);
    }

    /**
     * Accesso con la carica pubblica. La password viene azzerata prima di uscire, anche in caso di errore.
     * Il messaggio d'errore è lo stesso per qualsiasi combinazione sbagliata di credenziali.
     * Il conteggio per account non include il codice identificativo: provare tanti codici conta come errori.
     *
     * @throws TooManyAttemptsException se il dispositivo è bloccato per i troppi tentativi
     * @throws AuthenticationException  se le credenziali non sono corrette
     */
    public OfficeSession openOfficeSession(String username, String officeCode, char[] password,
                                           String device) throws AuthenticationException {
        try {
            Identity identity = authenticate(username, true, device, password,
                () -> gateway.authenticateOffice(username, officeCode, password));
            User user = createOfficeUser(identity);
            Cancellation cancellation = (user instanceof Judge && services != null)
                ? services.cancellationFor((Judge) user) : null;
            SpeedCameraManagement management = (user instanceof Administrator && speedCameraServices != null)
                ? speedCameraServices.managementFor((Administrator) user) : null;
            return new OfficeSession(user, new FineHistoryProxy(user, realHistory), cancellation, management);
        } finally {
            zeroOut(password);
        }
    }

    /**
     * Aspetta che gli avvisi accodati fino a questo momento siano stati consegnati.
     * Serve a test, demo e spegnimento ordinato (gli avvisi sono asincroni).
     */
    public void awaitAlerts() {
        try {
            alerts.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attesa degli avvisi interrotta", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Avvisi non consegnati", e);
        }
    }

    /**
     * Prenota il tentativo sul dispositivo (atomico: se è bloccato esce subito), controlla le credenziali e
     * aggiorna i contatori. Gli errori di credenziali hanno tutti lo stesso messaggio.
     */
    private Identity authenticate(String username, boolean officeAccount, String device, char[] password,
                                  Supplier<Optional<Identity>> attempt) throws AuthenticationException {
        if (!perDevice.tryAttempt(device)) {
            throw new TooManyAttemptsException();
        }
        Optional<Identity> outcome = (password == null) ? Optional.<Identity>empty() : attempt.get();
        String accountKey = (officeAccount ? "R:" : "C:") + username;
        if (!outcome.isPresent()) {
            // il tentativo è già stato contato dal dispositivo (tryAttempt)
            int errors = accountMonitor.registerFailure(accountKey);
            if (errors > 0) {
                alertOwner(username, officeAccount, errors);
            }
            throw new AuthenticationException(MESSAGE);
        }
        // un accesso riuscito restituisce il tentativo al dispositivo: non lo azzera, altrimenti basterebbe
        // intercalare un accesso al proprio account per "ricaricare" i tentativi
        perDevice.release(device);
        accountMonitor.registerSuccess(accountKey);
        return outcome.get();
    }

    private void alertOwner(String username, boolean officeAccount, int errors) {
        try {
            alerts.execute(() -> {
                try {
                    alert.failedAttempts(username, officeAccount, errors);
                } catch (RuntimeException e) {
                    System.err.println("Avviso al titolare non consegnato: " + e);
                }
            });
        } catch (RejectedExecutionException e) {
            System.err.println("Avviso al titolare scartato: " + e);
        }
    }

    private static void zeroOut(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    /** Unico punto in cui il ruolo salvato diventa il tipo di User (e quindi i suoi privilegi). */
    private User createOfficeUser(Identity identity) {
        switch (identity.getRole()) {
            case POLICE_OFFICER: return new PoliceOfficer(identity.getUserId(), identity.getUsername());
            case ADMINISTRATOR:  return new Administrator(identity.getUserId(), identity.getUsername());
            case JUDGE:          return new Judge(identity.getUserId(), identity.getUsername());
            default:
                throw new IllegalStateException("Ruolo di carica sconosciuto: " + identity.getRole());
        }
    }
}
