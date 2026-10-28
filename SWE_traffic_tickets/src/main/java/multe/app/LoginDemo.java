package multe.app;

import multe.login.AutenticazioneException;
import multe.login.DataBasePassword;
import multe.login.GatewayLogin;
import multe.login.Ruolo;
import multe.storico.LoginProxy;
import multe.storico.StoricoMulte;
import multe.utenti.Cittadino;
import multe.utenti.Utente;

import java.util.Collections;

public class LoginDemo {

    public static void main(String[] args) {
        DataBasePassword db = new DataBasePassword();
        db.registra("mario", "pwdMario1!".toCharArray(), 1, Ruolo.CITTADINO);
        db.registra("luca", "pwdLuca2!".toCharArray(), 2, Ruolo.CITTADINO);
        db.registra("rossi", "pwdRossi3!".toCharArray(), 3, Ruolo.CARABINIERE);
        db.registra("admin", "pwdAdmin4!".toCharArray(), 4, Ruolo.AMMINISTRATORE);

        // Storico reale finto, in attesa di StoricoMulteTotale
        StoricoMulte reale = id -> {
            System.out.println("  [storico reale] multe dell'utente " + id);
            return Collections.emptyList();
        };
        LoginProxy login = new LoginProxy(new GatewayLogin(db), reale);

        Utente mario = new Cittadino(1, "mario");
        Utente luca = new Cittadino(2, "luca");

        try {
            System.out.println("Mario fa login:");
            StoricoMulte storicoMario = login.login("mario", "pwdMario1!".toCharArray());
            storicoMario.getMulte(mario.getId());
            try {
                storicoMario.getMulte(luca.getId());
            } catch (SecurityException e) {
                System.out.println("  Negato: " + e.getMessage());
            }

            System.out.println("Il carabiniere fa login:");
            StoricoMulte storicoRossi = login.login("rossi", "pwdRossi3!".toCharArray());
            storicoRossi.getMulte(mario.getId());
            storicoRossi.getMulte(luca.getId());

            System.out.println("L'amministratore fa login:");
            login.login("admin", "pwdAdmin4!".toCharArray()).getMulte(luca.getId());
        } catch (AutenticazioneException e) {
            System.out.println("Errore inatteso: " + e.getMessage());
        }

        for (String[] t : new String[][] { {"mario", "sbagliata"}, {"inesistente", "qualsiasi"} }) {
            try {
                login.login(t[0], t[1].toCharArray());
            } catch (AutenticazioneException e) {
                System.out.println("Login " + t[0] + ": " + e.getMessage());
            }
        }
    }
}
