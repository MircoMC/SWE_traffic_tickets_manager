package fines.notifications;

import static fines.testutil.Infrazioni.crea;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;
import fines.domain.FineService;
import fines.payments.PaymentService;
import fines.persistence.InMemoryFineDAO;
import fines.plates.PlateRegistry;
import fines.plates.InMemoryPlateRegistry;
import org.junit.jupiter.api.Test;

/** L'id della multa è un segreto al portatore: chi lo conosce può pagarla. Non deve finire nell'output. */
class NessunIdNeiLogTest {

    private static final String FORMA_UUID = "(?s).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*";

    @Test
    void notifichePagamentoEErroriNonScrivonoMaiLIdDellaMulta() throws Exception {
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream catturato = new ByteArrayOutputStream();
        PrintStream cattura = new PrintStream(catturato, true, "UTF-8");
        System.setOut(cattura);
        System.setErr(cattura);
        try {
            PlateRegistry registro = new InMemoryPlateRegistry();
            registro.assign("AB123CD", 1);
            FineService servizi = new FineService(new InMemoryFineDAO(), registro, new PaymentService());
            servizi.addObserver(new UserNotifier());

            UUID id = crea(servizi, "AB123CD", 80);
            servizi.pay(id);
            assertThrows(IllegalStateException.class, () -> servizi.pay(id));   // già pagata: errore, niente nuovo addebito
            servizi.awaitNotifications();                                           // le notifiche sono asincrone
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        String testo = catturato.toString("UTF-8");
        assertTrue(testo.contains("Notifica a AB123CD: ISSUED"));
        assertTrue(testo.contains("Notifica a AB123CD: PAID"));
        assertTrue(testo.contains("Pagamento di"));
        assertFalse(testo.matches(FORMA_UUID));      // nessun UUID nell'output
    }
}
