package multe.dominio;

/** Scaduta ma ancora pagabile (con un costo maggiore, in futuro) e annullabile. */
final class Scaduta extends StatoMulta {

    private static final Scaduta INSTANCE = new Scaduta();

    private Scaduta() {}

    static Scaduta getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "SCADUTA";
    }

    @Override
    StatoMulta onPay() {
        return Pagata.getInstance();
    }

    @Override
    StatoMulta onCancel() {
        return Annullata.getInstance();
    }
}
