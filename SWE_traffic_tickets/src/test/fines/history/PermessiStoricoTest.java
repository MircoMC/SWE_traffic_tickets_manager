package fines.history;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import fines.domain.Fine;
import fines.domain.FineService;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import fines.testutil.GatewayContatore;
import fines.users.Citizen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermessiStoricoTest {

    private InMemoryFineDAO dao;
    private LoginProxy login;

    @BeforeEach
    void setUp() {
        dao = new InMemoryFineDAO();
        PlateRegistry registro = new InMemoryPlateRegistry();
        registro.assign("AB123CD", 1);
        registro.assign("EF456GH", 2);
        registro.assign("XY999ZZ", 3);                  // l'auto privata del carabiniere
        FineService servizi = new FineService(dao, registro, new GatewayContatore());
        servizi.createFine(fines.testutil.Infrazioni.di("AB123CD", 80, java.time.Clock.systemUTC()));
        servizi.createFine(fines.testutil.Infrazioni.di("EF456GH", 81, java.time.Clock.systemUTC()));
        servizi.createFine(fines.testutil.Infrazioni.di("EF456GH", 82, java.time.Clock.systemUTC()));
        servizi.createFine(fines.testutil.Infrazioni.di("XY999ZZ", 83, java.time.Clock.systemUTC()));

        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("rossi", "pwdRossi3!".toCharArray(), 3);
        db.register("giudice", "pwdGiudice5!".toCharArray(), 10);
        db.registerOffice("rossi", "C-300", "pwdCaricaRossi!".toCharArray(), Role.POLICE_OFFICER);
        db.registerOffice("giudice", "G-001", "pwdCaricaGiudice!".toCharArray(), Role.JUDGE);
        login = new LoginProxy(new LoginGateway(db), new FullFineHistory(dao), servizi);
    }

    @Test
    void ilCittadinoVedeSoloLeProprieMulte() throws Exception {
        List<Fine> mie = login.openSession("mario", "pwdMario1!".toCharArray()).getMyFines();
        assertEquals(1, mie.size());
        assertEquals(1, mie.get(0).getOwnerId());
    }

    @Test
    void ilProxyDelCittadinoRifiutaQualsiasiIdDiUnAltro() {
        FineHistoryProxy proxy = new FineHistoryProxy(new Citizen(1, "mario"), new FullFineHistory(dao));
        assertEquals(1, proxy.getFines(1).size());
        for (int id : new int[] { 2, 3, 10, 0, -1, Integer.MAX_VALUE }) {
            int daVedere = id;
            assertThrows(SecurityException.class, () -> proxy.getFines(daVedere));
        }
    }

    @Test
    void ilCarabiniereConLaCaricaVedeLeMulteDiTutti() throws Exception {
        FineHistory s = login.openOfficeSession("rossi", "C-300", "pwdCaricaRossi!".toCharArray()).getHistory();
        assertEquals(1, s.getFines(1).size());
        assertEquals(2, s.getFines(2).size());
    }

    @Test
    void ilGiudiceConLaCaricaVedeLeMulteDiTutti() throws Exception {
        FineHistory s = login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray()).getHistory();
        assertEquals(1, s.getFines(1).size());
        assertEquals(2, s.getFines(2).size());
    }

    @Test
    void ilCarabiniereCheEntraDaCittadinoVedeSoloLeSueMulte() throws Exception {
        List<Fine> sue = login.openSession("rossi", "pwdRossi3!".toCharArray()).getMyFines();
        assertEquals(1, sue.size());
        assertEquals(3, sue.get(0).getOwnerId());   // la sua auto privata, non le multe degli altri
    }

    @Test
    void leListeRestituiteNonSiPossonoModificare() throws Exception {
        List<Fine> mie = login.openSession("mario", "pwdMario1!".toCharArray()).getMyFines();
        assertThrows(UnsupportedOperationException.class, mie::clear);
        FineHistory s = login.openOfficeSession("rossi", "C-300", "pwdCaricaRossi!".toCharArray()).getHistory();
        assertThrows(UnsupportedOperationException.class, () -> s.getFines(2).clear());
    }
}
