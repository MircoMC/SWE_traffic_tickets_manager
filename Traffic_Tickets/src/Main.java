import java.time.LocalDate;

public class Main {

    public static void main(String[] args) {
        MapperId mapperId = new MapperId();
        MultaDAO dao = new MultaDAOInMemory();
        StateManager stateManager = new StateManager(mapperId, new PaymentService());
        MulteMaker maker = new MulteMaker(mapperId, dao);
        maker.addObserver(new PersistenzaObserver(dao));
        maker.addObserver(new NotificaUtente());

        Autovelox autovelox = new Autovelox("A1", 50, new Location("Via Roma 1", 43.77, 11.25));
        autovelox.setLimite(70);
        System.out.println("Limite autovelox " + autovelox.getId() + ": " + autovelox.getLimite());

        // Multa 1: emessa e pagata, poi un'operazione non consentita
        int id1 = maker.createMulta("AB123CD", 92);
        Multa m1 = mapperId.get(id1);
        System.out.println("Importo multa 1: " + euro(m1.getAmountCents()));
        System.out.println("Stato multa 1: " + stateManager.getState(id1).getState());
        stateManager.paymentStarting(id1);
        System.out.println("Stato multa 1: " + stateManager.getState(id1).getState());
        try {
            stateManager.cancel(id1);
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        try {
            stateManager.paymentStarting(id1);   // già pagata: nessun addebito
        } catch (IllegalStateException e) {
            System.out.println("Errore: " + e.getMessage());
        }
        // Una multa pagata oltre la scadenza non genera errori né cambi di stato
        stateManager.checkExpiration(id1, LocalDate.now().plusDays(90));

        // Multa 2: scade, ma si può ancora pagare
        int id2 = maker.createMulta("EF456GH", 81);
        stateManager.checkExpiration(id2, LocalDate.now().plusDays(61));
        System.out.println("Stato multa 2: " + stateManager.getState(id2).getState());
        stateManager.paymentStarting(id2);

        // Il DAO è rimasto allineato grazie a PersistenzaObserver
        dao.trovaPerId(id1).ifPresent(m ->
            System.out.println("DAO, multa " + id1 + ": " + m.getState().getState()));
        dao.trovaPerId(id2).ifPresent(m ->
            System.out.println("DAO, multa " + id2 + ": " + m.getState().getState()));
    }

    private static String euro(long cent) {
        return String.format("%d,%02d euro", cent / 100, cent % 100);
    }
}
