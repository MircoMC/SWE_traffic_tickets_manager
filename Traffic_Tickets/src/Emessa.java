public final class Emessa implements StatoMulta {

    private static final Emessa INSTANCE = new Emessa();

    private Emessa() {}

    public static Emessa getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "EMESSA";
    }

    @Override
    public boolean isPayable() {
        return true;
    }

    @Override
    public void pay(StateManager manager, Multa multa) {
        manager.changeState(multa, Pagata.getInstance(), Evento.PAGATA);
    }

    @Override
    public void cancel(StateManager manager, Multa multa) {
        manager.changeState(multa, Annullata.getInstance(), Evento.ANNULLATA);
    }

    @Override
    public void expire(StateManager manager, Multa multa) {
        manager.changeState(multa, Scaduta.getInstance(), Evento.SCADUTA);
    }
}
