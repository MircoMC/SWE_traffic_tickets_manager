package fines.app;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import fines.speedcamera.SpeedCamera;
import fines.speedcamera.Location;
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
import fines.history.Session;
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

        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1, Role.CITIZEN);
        db.register("judge", "pwdGiudice5!".toCharArray(), 10, Role.JUDGE);
        LoginProxy login = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao), services);

        SpeedCamera speedCamera = new SpeedCamera("A1", 50, new Location("Via Roma 1", 43.77, 11.25),
                                            services::createFine, clock);
        speedCamera.setLimit(70);
        System.out.println("Limite autovelox " + speedCamera.getId() + ": " + speedCamera.getLimit());
        System.out.println("65 km/h con limite 70: multa? " + speedCamera.detect("AB123CD", 65).isPresent());
        System.out.println("75 km/h con limite 70 (dentro il margine di 5): multa? " + speedCamera.detect("AB123CD", 75).isPresent());

        // Multa 1: emessa e pagata
        UUID id1 = speedCamera.detect("AB123CD", 92).orElseThrow();
        System.out.println("Importo multa 1: " + euro(services.getCopy(id1).getAmountCents()));
        System.out.println("Stato multa 1: " + services.getState(id1).getState());
        services.pay(id1);
        System.out.println("Stato multa 1: " + services.getState(id1).getState());
        try {
            services.pay(id1);   // già pagata: nessun addebito
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        UUID id2 = speedCamera.detect("EF456GH", 81).orElseThrow();
        UUID id3 = speedCamera.detect("AB123CD", 80).orElseThrow();

        // Annullamento: solo il giudice, dopo il login
        Session mario = login.openSession("mario", "pwdMario1!".toCharArray());
        System.out.println("Mario può annullare? " + mario.getCancellation().isPresent());
        Session judge = login.openSession("judge", "pwdGiudice5!".toCharArray());
        Cancellation cancellation = judge.getCancellation().orElseThrow(IllegalStateException::new);
        try {
            cancellation.cancel(id1, "Ricorso");   // una multa pagata non è annullabile
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        cancellation.cancel(id3, "Errore di rilevazione");
        System.out.println("Stato multa 3: " + services.getState(id3).getState() + " - "
            + services.getCopy(id3).getRuling().map(p -> p.getReason() + " (giudice " + p.getJudgeId() + ")").orElse("-"));

        // Scadenza: la data viene dal Clock, non dal chiamante
        clock.advance(Duration.ofDays(61));
        services.checkExpiration(id1);   // pagata: nessun effetto
        services.checkExpiration(id2);   // emessa: scade, ma si può ancora pagare
        services.checkExpiration(id3);   // annullata: nessun effetto
        System.out.println("Stato multa 2: " + services.getState(id2).getState());
        services.pay(id2);

        // Il DAO è sempre allineato: lo aggiorna lo StateManager a ogni transizione
        for (UUID id : new UUID[] { id1, id2, id3 }) {
            dao.findById(id).ifPresent(m ->
                System.out.println("DAO, multa " + id + ": " + m.getState().getState()));
        }
    }

    private static String euro(long cents) {
        return String.format("%d,%02d euro", cents / 100, cents % 100);
    }
}
