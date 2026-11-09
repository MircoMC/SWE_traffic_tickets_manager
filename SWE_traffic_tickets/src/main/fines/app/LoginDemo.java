package fines.app;

import fines.login.AuthenticationException;
import fines.login.EmailAlert;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.history.LoginProxy;
import fines.history.OfficeSession;
import fines.history.CitizenSession;
import fines.history.FineHistory;

import java.time.Clock;
import java.util.Collections;

public class LoginDemo {

    public static void main(String[] args) {
        PasswordDatabase db = new PasswordDatabase();
        // ogni persona ha l'account privato (cittadino)...
        db.register("mario", "pwdMario1!".toCharArray(), 1);
        db.register("luca", "pwdLuca2!".toCharArray(), 2);
        db.register("rossi", "pwdRossi3!".toCharArray(), 3);
        db.register("admin", "pwdAdmin4!".toCharArray(), 4);
        db.register("giudice", "pwdGiudice5!".toCharArray(), 5);
        // ...e chi ha una carica ne ha anche una con il codice identificativo (la password può essere uguale)
        db.registerOffice("rossi", "C-300", "pwdRossi3!".toCharArray(), Role.POLICE_OFFICER);
        db.registerOffice("admin", "A-001", "pwdCaricaAdmin!".toCharArray(), Role.ADMINISTRATOR);
        db.registerOffice("giudice", "G-001", "pwdCaricaGiudice!".toCharArray(), Role.JUDGE);

        // Storico reale finto, in attesa di FullFineHistory
        FineHistory real = id -> {
            System.out.println("  [storico reale] multe dell'utente " + id);
            return Collections.emptyList();
        };
        LoginProxy login = new LoginProxy(new LoginGateway(db), real, null, null, Clock.systemUTC(), new EmailAlert());

        try {
            System.out.println("Mario fa login da cittadino:");
            CitizenSession mario = login.openSession("mario", "pwdMario1!".toCharArray());
            mario.getMyFines();

            System.out.println("Il carabiniere entra da cittadino (come tutti):");
            login.openSession("rossi", "pwdRossi3!".toCharArray()).getMyFines();

            System.out.println("Il carabiniere entra con la carica:");
            OfficeSession rossi = login.openOfficeSession("rossi", "C-300", "pwdRossi3!".toCharArray());
            rossi.getHistory().getFines(1);
            rossi.getHistory().getFines(2);

            System.out.println("L'amministratore entra con la carica:");
            login.openOfficeSession("admin", "A-001", "pwdCaricaAdmin!".toCharArray()).getHistory().getFines(2);

            OfficeSession judge = login.openOfficeSession("giudice", "G-001", "pwdCaricaGiudice!".toCharArray());
            System.out.println("Il giudice con la carica può annullare? " + judge.getCancellation().isPresent()
                + " (qui non ci sono i servizi, quindi no: vedi Main)");
        } catch (AuthenticationException e) {
            System.out.println("Errore inatteso: " + e.getMessage());
        }

        String[][] wrong = {
            {"mario", null, "sbagliata"},                      // password errata
            {"inesistente", null, "qualsiasi"},                // utente inesistente
            {"rossi", "C-999", "pwdRossi3!"},                  // codice identificativo errato
            {"mario", "C-300", "pwdMario1!"},                  // mario non ha quella carica
        };
        for (String[] t : wrong) {
            try {
                if (t[1] == null) {
                    login.openSession(t[0], t[2].toCharArray());
                } else {
                    login.openOfficeSession(t[0], t[1], t[2].toCharArray());
                }
            } catch (AuthenticationException e) {
                System.out.println("Accesso rifiutato: " + e.getMessage());
            }
        }
        // troppi tentativi da uno stesso dispositivo: il dispositivo viene bloccato
        for (int i = 1; i <= 4; i++) {
            try {
                login.openSession("mario", "sbagliata".toCharArray(), "dispositivo-1");
            } catch (AuthenticationException e) {
                System.out.println("Tentativo " + i + " dal dispositivo-1: " + e.getMessage());
            }
        }
        // tentativi da tanti dispositivi diversi: nessun blocco, ma il titolare viene avvisato (una volta sola)
        for (int i = 0; i < 12; i++) {
            try {
                login.openSession("luca", "sbagliata".toCharArray(), "d-" + i);
            } catch (AuthenticationException e) {
                // tentativi falliti
            }
        }
        login.awaitAlerts();
        System.out.println("Luca con la password giusta, da un nuovo dispositivo: "
            + safeLogin(login, "luca", "pwdLuca2!", "nuovo"));
    }

    private static String safeLogin(LoginProxy login, String fiscalCode, String password, String device) {
        try {
            login.openSession(fiscalCode, password.toCharArray(), device);
            return "accesso riuscito (l'account non è stato bloccato)";
        } catch (AuthenticationException e) {
            return "rifiutato: " + e.getMessage();
        }
    }
}
