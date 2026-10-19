public class Scaduta implements StatoMulta {

    private static final Scaduta INSTANCE = new Scaduta();

    private Scaduta() {}

    public static Scaduta getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() { // <-- deve chiamarsi getState()
        return "SCADUTA";
    }

    @Override
    public void pay(Multa multa) {
        multa.changeState(Pagata.getInstance());
    }

    @Override
    public void cancel(Multa multa) {
        multa.changeState(Annullata.getInstance());
    }
}