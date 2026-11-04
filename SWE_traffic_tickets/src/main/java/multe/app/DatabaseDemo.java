package fines.app;

import fines.domain.Cancellation;
import fines.domain.Fine;
import fines.domain.FineService;
import fines.login.LoginGateway;
import fines.login.PasswordDatabase;
import fines.notifications.UserNotifier;
import fines.payments.PaymentService;
import fines.persistence.ConnectionProvider;
import fines.persistence.JdbcCredentialsStore;
import fines.persistence.JdbcFineDAO;
import fines.persistence.JdbcFineEventLog;
import fines.persistence.JdbcPlateRegistry;
import fines.history.FullFineHistory;
import fines.history.LoginProxy;
import fines.history.Session;
import fines.speedcamera.Location;
import fines.speedcamera.SpeedCamera;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Demo on a real PostgreSQL database (create it with db/schema.sql and db/data.sql).
 * Usage: DatabaseDemo [jdbcUrl user password]  - default: jdbc:postgresql://localhost:5432/fines postgres postgres
 * It can be run many times: every run creates new fines, the seed data is only read.
 */
public class DatabaseDemo {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "jdbc:postgresql://localhost:5432/fines";
        String user = args.length > 1 ? args[1] : "postgres";
        String password = args.length > 2 ? args[2] : "postgres";

        ConnectionProvider db = ConnectionProvider.of(url, user, password);
        Clock clock = Clock.systemUTC();
        JdbcFineDAO dao = new JdbcFineDAO(db);

        FineService service = new FineService(dao, new JdbcPlateRegistry(db, clock), new PaymentService(), clock);
        service.addObserver(new JdbcFineEventLog(db, clock));
        service.addObserver(new UserNotifier());
        // Corretto per il DatabaseDemo (usando la versione JDBC del CredentialsStore):
        LoginProxy login = new LoginProxy(new LoginGateway(new JdbcCredentialsStore(db)),
                new FullFineHistory(dao), service);

        Session mario = login.openSession("RSSMRA80A01H501U", "pwdMario1!".toCharArray());
        print("Fines of Mario (from the database)", mario.getMyFines());

        // The owner is read from the plate history in the database.
        SpeedCamera camera = new SpeedCamera("AVX-001", 50, new Location("Via Roma 1", 41.90, 12.49),
                                             service::createFine, clock);

        UUID newFine = camera.detect("AB123CD", 92)
                .orElseThrow(() -> new IllegalStateException("Nessuna multa generata"));
        System.out.println("\nNew fine " + newFine + " for plate AB123CD, paying it:");
        service.pay(newFine);

        UUID other = camera.detect("EF456GH", 80)
                .orElseThrow(() -> new IllegalStateException("Nessuna multa generata"));
        Session judge = login.openSession("MRTGPP65M10H501Q", "pwdGiuseppe7!".toCharArray());
        Cancellation cancellation = judge.getCancellation().orElseThrow(IllegalStateException::new);
        cancellation.cancel(other, "Detection error");
        System.out.println("Fine " + other + " cancelled by the judge");

        print("\nFines of Mario now", mario.getMyFines());
    }

    private static void print(String title, List<Fine> fines) {
        System.out.println(title + ":");
        for (Fine f : fines) {
            System.out.println("  fine " + f.getId() + " plate " + f.getPlate()
                + " " + f.getState().getState() + " " + f.getAmountCents() / 100 + " EUR");
        }
    }
}
