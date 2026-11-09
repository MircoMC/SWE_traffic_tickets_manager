package fines.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import fines.app.AdjustableClock;
import fines.speedcamera.SpeedCameraMapAdapter;
import fines.speedcamera.CachedSpeedCameraMap;
import fines.speedcamera.SpeedCameraManagement;
import fines.speedcamera.ExternalMapper;
import fines.speedcamera.Period;
import fines.speedcamera.SpeedCameraServices;
import fines.speedcamera.Location;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.persistence.InMemoryFineDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Chi può programmare la configurazione degli autovelox: solo l'amministratore entrato con la carica. */
class GestioneAutoveloxAccessoTest {

    private AdjustableClock clock;
    private ExternalMapper esterno;
    private CachedSpeedCameraMap mappa;
    private PasswordDatabase db;
    private LoginProxy login;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock();
        esterno = new ExternalMapper(clock);
        esterno.load("A1", "2020-01-01T00:00:00Z", "50", "43.77", "11.25", "Via Roma 1");
        mappa = new CachedSpeedCameraMap(new SpeedCameraMapAdapter(esterno), clock);

        db = new PasswordDatabase();
        db.register("admin", "pwdAdmin4!".toCharArray(), 4);
        db.register("giudice", "pwdGiudice5!".toCharArray(), 10);
        db.register("rossi", "pwdRossi3!".toCharArray(), 3);
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.registerOffice("admin", "A-001", "pwdCaricaAdmin!".toCharArray(), Role.ADMINISTRATOR);
        db.registerOffice("giudice", "G-001", "pwdCaricaGiudice!".toCharArray(), Role.JUDGE);
        db.registerOffice("rossi", "C-300", "pwdCaricaRossi!".toCharArray(), Role.POLICE_OFFICER);
        login = new LoginProxy(new LoginGateway(db), new FullFineHistory(new InMemoryFineDAO()),
            null, new SpeedCameraServices(mappa, clock));
    }

    @Test
    void soloLAmministratoreConLaCaricaOttieneLaGestioneDegliAutovelox() throws Exception {
        assertTrue(login.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray())
            .getSpeedCameraManagement().isPresent());
        assertFalse(login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray())
            .getSpeedCameraManagement().isPresent());
        assertFalse(login.openOfficeSession("rossi", "C-300", "pwdCaricaRossi!".toCharArray())
            .getSpeedCameraManagement().isPresent());
    }

    @Test
    void senzaIServiziNessunoLaOttiene() throws Exception {
        LoginProxy senza = new LoginProxy(new LoginGateway(db), new FullFineHistory(new InMemoryFineDAO()));
        assertFalse(senza.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray())
            .getSpeedCameraManagement().isPresent());
    }

    @Test
    void lAmministratoreProgrammaDalleSuaSessioneEIlPassatoRestaIntatto() throws Exception {
        SpeedCameraManagement gestione = login.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray())
            .getSpeedCameraManagement().get();
        Instant ora = clock.instant();
        Location pos = new Location("Via Roma 1", 43.77, 11.25);

        gestione.schedule("A1", ora.plus(Duration.ofHours(1)), 70, pos);
        assertThrows(IllegalArgumentException.class, () -> gestione.schedule("A1", ora.minus(Duration.ofDays(1)), 200, pos));

        assertEquals(50, Period.configurationAt(gestione.history("A1"), ora).get().getLimit());
        assertEquals(70, Period.configurationAt(gestione.history("A1"), ora.plus(Duration.ofHours(2))).get().getLimit());
    }

    @Test
    void ilCittadinoCheHaLaCaricaDiAmministratoreNonLaOttieneEntrandoDaCittadino() throws Exception {
        // la CitizenSession non ha nemmeno il metodo: qui si controlla che l'accesso privato non apra la carica
        assertNotNull(login.openSession("admin", "pwdAdmin4!".toCharArray()));
        assertThrows(fines.login.AuthenticationException.class,
            () -> login.openOfficeSession("admin", "A-001", "pwdAdmin4!".toCharArray()));   // password privata diversa da quella della carica
    }
}
