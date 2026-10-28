package multe.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import multe.autovelox.Autovelox;
import multe.autovelox.Location;
import multe.dominio.Multa;
import multe.dominio.MultaDAO;
import multe.dominio.Rilevazione;
import multe.dominio.TargaNonRegistrataException;
import multe.dominio.ServiziMulte;
import multe.login.DataBasePassword;
import multe.login.GatewayLogin;
import multe.login.Ruolo;
import multe.pagamenti.PaymentService;
import multe.persistenza.MultaDAOInMemory;
import multe.storico.LoginProxy;
import multe.storico.StoricoMulte;
import multe.storico.StoricoMulteTotale;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;
import multe.utenti.Cittadino;
import multe.utenti.Utente;

public class DemoTarghe {

    public static void main(String[] args) throws Exception {
        Clock clock = Clock.systemUTC();
        MultaDAO dao = new MultaDAOInMemory();
        RegistroTarghe registro = new RegistroTargheInMemory(clock);
        Instant inizio = clock.instant().minus(Duration.ofDays(365));    // proprietari da un anno
        registro.assegna("AB123CD", 1, inizio);
        registro.assegna("XY999ZZ", 1, inizio);      // mario ha due targhe
        registro.assegna("EF456GH", 2, inizio);
        ServiziMulte servizi = new ServiziMulte(dao, registro, new PaymentService(), clock);
        Autovelox autovelox = new Autovelox("A1", 50, new Location("Via Roma 1", 43.77, 11.25),
                                            servizi::creaMulta, clock);
        StoricoMulte storico = new StoricoMulteTotale(dao);

        Utente mario = new Cittadino(1, "mario");
        Utente luca = new Cittadino(2, "luca");

        autovelox.rileva("AB123CD", 92);
        autovelox.rileva(" xy999zz", 80);   // la targa viene normalizzata
        autovelox.rileva("EF456GH", 81);
        System.out.println("Targhe di mario: " + registro.targheDi(1));
        stampa("Multe di mario", storico.getMulte(mario.getId()));
        stampa("Multe di luca", storico.getMulte(luca.getId()));

        System.out.println("\nMario vende AB123CD a luca, poi nuova infrazione:");
        registro.assegna("AB123CD", 2);
        autovelox.rileva("AB123CD", 70);
        System.out.println("\nArriva in ritardo una rilevazione di 2 giorni fa (prima della vendita):");
        servizi.creaMulta(new Rilevazione("A1", "AB123CD", 85, 50, clock.instant().minus(Duration.ofDays(2))));
        stampa("Multe di mario", storico.getMulte(mario.getId()));
        stampa("Multe di luca", storico.getMulte(luca.getId()));

        try {
            autovelox.rileva("ZZ000ZZ", 90);
        } catch (TargaNonRegistrataException e) {
            System.out.println("\nErrore: " + e.getMessage());
        }

        System.out.println("\nCon il login:");
        DataBasePassword db = new DataBasePassword();
        db.registra("mario", "pwdMario1!".toCharArray(), 1, Ruolo.CITTADINO);
        LoginProxy login = new LoginProxy(new GatewayLogin(db), storico);
        StoricoMulte s = login.login("mario", "pwdMario1!".toCharArray());
        stampa("Mario vede", s.getMulte(mario.getId()));
        try {
            s.getMulte(luca.getId());
        } catch (SecurityException e) {
            System.out.println("Negato: " + e.getMessage());
        }
    }

    private static void stampa(String titolo, List<Multa> multe) {
        System.out.println(titolo + ":");
        for (Multa m : multe) {
            System.out.println("  multa " + m.getId() + " targa " + m.getPlate()
                + " intestata a " + m.getIdIntestatario());
        }
    }
}
