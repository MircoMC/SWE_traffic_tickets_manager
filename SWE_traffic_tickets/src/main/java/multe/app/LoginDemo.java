package fines.app;

import fines.login.AuthenticationException;
import fines.login.PasswordDatabase;
import fines.login.LoginGateway;
import fines.login.Role;
import fines.history.LoginProxy;
import fines.history.FineHistory;
import fines.users.Citizen;
import fines.users.User;

import java.util.Collections;

public class LoginDemo {

    public static void main(String[] args) {
        PasswordDatabase db = new PasswordDatabase();
        db.register("mario", "pwdMario1!".toCharArray(), 1, Role.CITIZEN);
        db.register("luca", "pwdLuca2!".toCharArray(), 2, Role.CITIZEN);
        db.register("rossi", "pwdRossi3!".toCharArray(), 3, Role.POLICE_OFFICER);
        db.register("admin", "pwdAdmin4!".toCharArray(), 4, Role.ADMINISTRATOR);

        // Storico reale finto, in attesa di FullFineHistory
        FineHistory real = id -> {
            System.out.println("  [storico reale] multe dell'utente " + id);
            return Collections.emptyList();
        };
        LoginProxy login = new LoginProxy(new LoginGateway(db), real);

        User mario = new Citizen(1, "mario");
        User luca = new Citizen(2, "luca");

        try {
            System.out.println("Mario fa login:");
            FineHistory historyMario = login.login("mario", "pwdMario1!".toCharArray());
            historyMario.getFines(mario.getId());
            try {
                historyMario.getFines(luca.getId());
            } catch (SecurityException e) {
                System.out.println("  Negato: " + e.getMessage());
            }

            System.out.println("Il carabiniere fa login:");
            FineHistory historyRossi = login.login("rossi", "pwdRossi3!".toCharArray());
            historyRossi.getFines(mario.getId());
            historyRossi.getFines(luca.getId());

            System.out.println("L'amministratore fa login:");
            login.login("admin", "pwdAdmin4!".toCharArray()).getFines(luca.getId());
        } catch (AuthenticationException e) {
            System.out.println("Errore inatteso: " + e.getMessage());
        }

        for (String[] t : new String[][] { {"mario", "sbagliata"}, {"inesistente", "qualsiasi"} }) {
            try {
                login.login(t[0], t[1].toCharArray());
            } catch (AuthenticationException e) {
                System.out.println("Login " + t[0] + ": " + e.getMessage());
            }
        }
    }
}
