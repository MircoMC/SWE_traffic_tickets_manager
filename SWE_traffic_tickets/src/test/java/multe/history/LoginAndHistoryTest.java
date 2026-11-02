package fines.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fines.domain.Detection;
import fines.domain.FineDAO;
import fines.domain.FineService;
import fines.login.AuthenticationException;
import fines.login.LoginGateway;
import fines.login.PasswordDatabase;
import fines.login.Role;
import fines.payments.PaymentService;
import fines.persistence.InMemoryFineDAO;
import fines.plates.InMemoryPlateRegistry;
import fines.plates.PlateRegistry;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAndHistoryTest {

    private LoginProxy login;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        FineDAO dao = new InMemoryFineDAO();
        PlateRegistry registry = new InMemoryPlateRegistry(clock);
        registry.assign("AB123CD", 1, clock.instant().minusSeconds(3600));
        registry.assign("EF456GH", 2, clock.instant().minusSeconds(3600));
        FineService service = new FineService(dao, registry, new PaymentService(), clock);
        service.createFine(new Detection("A1", "AB123CD", 90, 50, clock.instant()));
        service.createFine(new Detection("A1", "EF456GH", 90, 50, clock.instant()));

        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1, Role.CITIZEN);
        db.register("rossi", "pwdRossi3!".toCharArray(), 3, Role.POLICE_OFFICER);
        db.register("judge", "pwdJudge5!".toCharArray(), 10, Role.JUDGE);
        login = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao), service);
    }

    @Test
    void citizenSeesOnlyOwnFines() throws Exception {
        Session session = login.openSession("mario", "pwdMario1!".toCharArray());
        assertEquals(1, session.getMyFines().size());
        assertThrows(SecurityException.class, () -> session.getHistory().getFines(2));
    }

    @Test
    void policeOfficerSeesEveryonesFines() throws Exception {
        FineHistory history = login.login("rossi", "pwdRossi3!".toCharArray());
        assertEquals(1, history.getFines(1).size());
        assertEquals(1, history.getFines(2).size());
    }

    @Test
    void onlyTheJudgeGetsTheCancellationCapability() throws Exception {
        assertFalse(login.openSession("mario", "pwdMario1!".toCharArray()).getCancellation().isPresent());
        assertFalse(login.openSession("rossi", "pwdRossi3!".toCharArray()).getCancellation().isPresent());
        assertTrue(login.openSession("judge", "pwdJudge5!".toCharArray()).getCancellation().isPresent());
    }

    @Test
    void wrongPasswordAndUnknownUserFailWithTheSameMessage() {
        AuthenticationException wrong = assertThrows(AuthenticationException.class,
            () -> login.login("mario", "wrong".toCharArray()));
        AuthenticationException unknown = assertThrows(AuthenticationException.class,
            () -> login.login("nobody", "whatever".toCharArray()));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void passwordArrayIsClearedAfterLogin() throws Exception {
        char[] password = "pwdMario1!".toCharArray();
        login.login("mario", password);
        for (char c : password) {
            assertEquals('\0', c);
        }
    }

    @Test
    void returnedListCannotBeModified() throws Exception {
        FineHistory history = login.login("mario", "pwdMario1!".toCharArray());
        assertThrows(UnsupportedOperationException.class, () -> history.getFines(1).clear());
    }
}
