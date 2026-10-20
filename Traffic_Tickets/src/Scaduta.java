public final class Scaduta implements StatoMulta {

    private static final Scaduta INSTANCE = new Scaduta();

    private Scaduta() {}

    public static Scaduta getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "SCADUTA";
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
}
