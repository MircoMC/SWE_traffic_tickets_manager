package fines.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import fines.speedcamera.SpeedCamera;
import fines.speedcamera.Location;
import fines.domain.Fine;
import fines.domain.FineDAO;
import fines.domain.Detection;
import fines.domain.UnregisteredPlateException;
import fines.domain.FineService;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.payments.PaymentService;
import fines.persistence.InMemoryFineDAO;
import fines.history.LoginProxy;
import fines.history.FineHistory;
import fines.history.FullFineHistory;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.users.Citizen;
import fines.users.User;

public class PlatesDemo {

    public static void main(String[] args) throws Exception {
        Clock clock = Clock.systemUTC();
        FineDAO dao = new InMemoryFineDAO();
        PlateRegistry registry = new InMemoryPlateRegistry(clock);
        Instant start = clock.instant().minus(Duration.ofDays(365));    // proprietari da un anno
        registry.assign("AB123CD", 1, start);
        registry.assign("XY999ZZ", 1, start);      // mario ha due targhe
        registry.assign("EF456GH", 2, start);
        FineService services = new FineService(dao, registry, new PaymentService(), clock);
        SpeedCamera speedCamera = new SpeedCamera("A1", 50, new Location("Via Roma 1", 43.77, 11.25),
                                            services::createFine, clock);
        FineHistory history = new FullFineHistory(dao);

        User mario = new Citizen(1, "mario");
        User luca = new Citizen(2, "luca");

        speedCamera.detect("AB123CD", 92);
        speedCamera.detect(" xy999zz", 80);   // la targa viene normalizzata
        speedCamera.detect("EF456GH", 81);
        System.out.println("Targhe di mario: " + registry.platesOf(1));
        print("Multe di mario", history.getFines(mario.getId()));
        print("Multe di luca", history.getFines(luca.getId()));

        System.out.println("\nMario vende AB123CD a luca, poi nuova infrazione:");
        registry.assign("AB123CD", 2);
        speedCamera.detect("AB123CD", 70);
        System.out.println("\nArriva in ritardo una rilevazione di 2 giorni fa (prima della vendita):");
        services.createFine(new Detection("A1", "AB123CD", 85, 50, clock.instant().minus(Duration.ofDays(2))));
        print("Multe di mario", history.getFines(mario.getId()));
        print("Multe di luca", history.getFines(luca.getId()));

        try {
            speedCamera.detect("ZZ000ZZ", 90);
        } catch (UnregisteredPlateException e) {
            System.out.println("\nErrore: " + e.getMessage());
        }

        System.out.println("\nCon il login:");
        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1, Role.CITIZEN);
        LoginProxy login = new LoginProxy(new LoginGateway(db), history);
        FineHistory s = login.login("mario", "pwdMario1!".toCharArray());
        print("Mario vede", s.getFines(mario.getId()));
        try {
            s.getFines(luca.getId());
        } catch (SecurityException e) {
            System.out.println("Negato: " + e.getMessage());
        }
    }

    private static void print(String title, List<Fine> fines) {
        System.out.println(title + ":");
        for (Fine m : fines) {
            System.out.println("  multa " + m.getId() + " targa " + m.getPlate()
                + " intestata a " + m.getOwnerId());
        }
    }
}
