package multe.app;

import java.time.Duration;
import multe.autovelox.Autovelox;
import multe.autovelox.Location;
import multe.dominio.Annullamento;
import multe.dominio.MultaDAO;
import multe.dominio.ServiziMulte;
import multe.login.DataBasePassword;
import multe.login.GatewayLogin;
import multe.login.Ruolo;
import multe.notifiche.NotificaUtente;
import multe.pagamenti.PaymentService;
import multe.persistenza.MultaDAOInMemory;
import multe.storico.LoginProxy;
import multe.storico.Sessione;
import multe.storico.StoricoMulteTotale;
import multe.targhe.RegistroTarghe;
import multe.targhe.RegistroTargheInMemory;

public class Main {

    public static void main(String[] args) throws Exception {
        MultaDAO dao = new MultaDAOInMemory();
        OrologioModificabile orologio = new OrologioModificabile();
        RegistroTarghe registro = new RegistroTargheInMemory(orologio);
        registro.assegna("AB123CD", 1);
        registro.assegna("EF456GH", 2);

        ServiziMulte servizi = new ServiziMulte(dao, registro, new PaymentService(), orologio);
        servizi.aggiungiOsservatore(new NotificaUtente());

        DataBasePassword db = new DataBasePassword();
        db.registra("mario", "pwdMario1!".toCharArray(), 1, Ruolo.CITTADINO);
        db.registra("giudice", "pwdGiudice5!".toCharArray(), 10, Ruolo.GIUDICE);
        LoginProxy login = new LoginProxy(new GatewayLogin(db), new StoricoMulteTotale(dao), servizi);

        Autovelox autovelox = new Autovelox("A1", 50, new Location("Via Roma 1", 43.77, 11.25),
                                            servizi::creaMulta, orologio);
        autovelox.setLimite(70);
        System.out.println("Limite autovelox " + autovelox.getId() + ": " + autovelox.getLimite());
        System.out.println("65 km/h con limite 70: multa? " + autovelox.rileva("AB123CD", 65).isPresent());
        System.out.println("75 km/h con limite 70 (dentro il margine di 5): multa? " + autovelox.rileva("AB123CD", 75).isPresent());

        // Multa 1: emessa e pagata
        int id1 = autovelox.rileva("AB123CD", 92).getAsInt();
        System.out.println("Importo multa 1: " + euro(servizi.getCopia(id1).getAmountCents()));
        System.out.println("Stato multa 1: " + servizi.getStato(id1).getState());
        servizi.paga(id1);
        System.out.println("Stato multa 1: " + servizi.getStato(id1).getState());
        try {
            servizi.paga(id1);   // già pagata: nessun addebito
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        int id2 = autovelox.rileva("EF456GH", 81).getAsInt();
        int id3 = autovelox.rileva("AB123CD", 80).getAsInt();

        // Annullamento: solo il giudice, dopo il login
        Sessione mario = login.accedi("mario", "pwdMario1!".toCharArray());
        System.out.println("Mario può annullare? " + mario.getAnnullamento().isPresent());
        Sessione giudice = login.accedi("giudice", "pwdGiudice5!".toCharArray());
        Annullamento annullamento = giudice.getAnnullamento().orElseThrow(IllegalStateException::new);
        try {
            annullamento.annulla(id1, "Ricorso");   // una multa pagata non è annullabile
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        annullamento.annulla(id3, "Errore di rilevazione");
        System.out.println("Stato multa 3: " + servizi.getStato(id3).getState() + " - "
            + servizi.getCopia(id3).getProvvedimento().map(p -> p.getMotivazione() + " (giudice " + p.getIdGiudice() + ")").orElse("-"));

        // Scadenza: la data viene dal Clock, non dal chiamante
        orologio.avanza(Duration.ofDays(61));
        servizi.verificaScadenza(id1);   // pagata: nessun effetto
        servizi.verificaScadenza(id2);   // emessa: scade, ma si può ancora pagare
        servizi.verificaScadenza(id3);   // annullata: nessun effetto
        System.out.println("Stato multa 2: " + servizi.getStato(id2).getState());
        servizi.paga(id2);

        // Il DAO è sempre allineato: lo aggiorna lo StateManager a ogni transizione
        for (int id : new int[] { id1, id2, id3 }) {
            dao.trovaPerId(id).ifPresent(m ->
                System.out.println("DAO, multa " + id + ": " + m.getState().getState()));
        }
    }

    private static String euro(long cent) {
        return String.format("%d,%02d euro", cent / 100, cent % 100);
    }
}
