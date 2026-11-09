package fines.app;

import java.time.Duration;
import fines.speedcamera.Location;
import java.util.UUID;
import fines.speedcamera.SpeedCamera;
import fines.speedcamera.SpeedCameraMapAdapter;
import fines.speedcamera.CachedSpeedCameraMap;
import fines.speedcamera.SpeedCheck;
import fines.speedcamera.SpeedCameraManagement;
import fines.speedcamera.ExternalMapper;
import fines.speedcamera.SpeedCameraServices;
import fines.speedcamera.Period;
import fines.domain.Cancellation;
import fines.domain.FineDAO;
import fines.domain.FineService;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.notifications.UserNotifier;
import fines.payments.PaymentService;
import fines.persistence.InMemoryFineDAO;
import fines.history.LoginProxy;
import fines.history.OfficeSession;
import fines.history.CitizenSession;
import fines.history.FullFineHistory;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;

public class Main {

    public static void main(String[] args) throws Exception {
        FineDAO dao = new InMemoryFineDAO();
        AdjustableClock clock = new AdjustableClock();
        PlateRegistry registry = new InMemoryPlateRegistry(clock);
        registry.assign("AB123CD", 1);
        registry.assign("EF456GH", 2);

        FineService services = new FineService(dao, registry, new PaymentService(), clock);
        services.addObserver(new UserNotifier());

        // Il limite e la posizione non stanno nell'autovelox: stanno nel mapper esterno, associati all'id dell'autovelox
        ExternalMapper external = new ExternalMapper(clock);
        external.load("A1", "2020-01-01T00:00:00Z", "50", "43.77", "11.25", "Via Roma 1");   // dati iniziali
        CachedSpeedCameraMap map = new CachedSpeedCameraMap(new SpeedCameraMapAdapter(external), clock);   // cache di un giorno
        SpeedCameraServices speedCameraServices = new SpeedCameraServices(map, clock);
        SpeedCheck check = new SpeedCheck(map, services::createFine);
        SpeedCamera speedCamera = new SpeedCamera("A1", check::evaluate, clock);

        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("giudice", "pwdGiudice5!".toCharArray(), 10);                                 // il giudice è anche un cittadino
        db.registerOffice("giudice", "G-001", "pwdCarica5!".toCharArray(), Role.JUDGE);       // e ha la carica
        db.register("admin", "pwdAdmin4!".toCharArray(), 4);
        db.registerOffice("admin", "A-001", "pwdCaricaAdmin!".toCharArray(), Role.ADMINISTRATOR);
        LoginProxy login = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao), services, speedCameraServices);

        // Solo l'amministratore con la carica programma i cambi di limite, e solo nel futuro
        System.out.println("Il giudice può gestire gli autovelox? "
            + login.openOfficeSession("giudice", "G-001", "pwdCarica5!".toCharArray()).getSpeedCameraManagement().isPresent());
        SpeedCameraManagement management = login.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray())
            .getSpeedCameraManagement().orElseThrow(IllegalStateException::new);
        try {
            management.schedule("A1", clock.instant().minus(Duration.ofDays(30)), 200, new Location("Via Roma 1", 43.77, 11.25));
        } catch (IllegalArgumentException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        management.schedule("A1", clock.instant().plus(Duration.ofHours(1)), 70, new Location("Via Roma 1", 43.77, 11.25));
        clock.advance(Duration.ofHours(2));      // il cambio è passato: ora vale il limite 70
        System.out.println("Limite autovelox " + speedCamera.getId() + ": "
            + Period.configurationAt(management.history("A1"), clock.instant()).get().getLimit());
        System.out.println("65 km/h con limite 70: multa? " + speedCamera.detect("AB123CD", 65).isPresent());
        System.out.println("75 km/h con limite 70 (dentro il margine di 5): multa? " + speedCamera.detect("AB123CD", 75).isPresent());

        // Fine 1: emessa e pagata
        UUID id1 = speedCamera.detect("AB123CD", 92).get();
        services.awaitNotifications();
        System.out.println("Importo multa 1: " + euro(services.getCopy(id1).getAmountCents()));
        System.out.println("Stato multa 1: " + services.getState(id1).getState());
        services.pay(id1);
        services.awaitNotifications();
        System.out.println("Stato multa 1: " + services.getState(id1).getState());
        try {
            services.pay(id1);   // già pagata: nessun addebito
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        UUID id2 = speedCamera.detect("EF456GH", 81).get();
        UUID id3 = speedCamera.detect("AB123CD", 80).get();

        services.awaitNotifications();

        // Il cittadino vede e paga le sue multe; l'annullamento è solo del giudice, entrando con la carica
        CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
        System.out.println("Mario (cittadino) vede " + mario.getMyFines().size() + " multe");
        OfficeSession judge = login.openOfficeSession("giudice", "G-001", "pwdCarica5!".toCharArray());
        Cancellation cancellation = judge.getCancellation().orElseThrow(IllegalStateException::new);
        try {
            cancellation.cancel(id1, "Ricorso");   // una multa pagata non è annullabile
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        cancellation.cancel(id3, "Errore di rilevazione");
        services.awaitNotifications();
        System.out.println("Stato multa 3: " + services.getState(id3).getState() + " - "
            + services.getCopy(id3).getRuling().map(p -> p.getReason() + " (giudice " + p.getJudgeId() + ")").orElse("-"));

        // Scadenza: la data viene dal Clock, non dal chiamante
        clock.advance(Duration.ofDays(61));
        services.checkExpiration(id1);   // pagata: nessun effetto
        services.checkExpiration(id2);   // emessa: scade, ma si può ancora pagare
        services.checkExpiration(id3);   // annullata: nessun effetto
        services.awaitNotifications();
        System.out.println("Stato multa 2: " + services.getState(id2).getState());
        services.pay(id2);
        services.awaitNotifications();

        // Il DAO è sempre allineato: lo aggiorna lo StateManager a ogni transizione
        UUID[] all = { id1, id2, id3 };
        for (int i = 0; i < all.length; i++) {
            int number = i + 1;
            dao.findById(all[i]).ifPresent(m ->
                System.out.println("DAO, multa " + number + ": " + m.getState().getState()));
        }
    }

    private static String euro(long cents) {
        return String.format("%d,%02d euro", cents / 100, cents % 100);
    }
}
