public class Emessa implements StatoMulta {

    private static final Emessa INSTANCE = new Emessa();

    private Emessa() {}

    public static Emessa getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() { // <-- deve chiamarsi getState()
        return "EMESSA";
    }

    @Override
    public void pay(Multa multa) {
        multa.changeState(Pagata.getInstance());
    }

    @Override
    public void cancel(Multa multa) {
        multa.changeState(Annullata.getInstance());
    }

    @Override
    public void expire(Multa multa) {
        multa.changeState(Scaduta.getInstance());
    }
}