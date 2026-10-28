package multe.dominio;

final class Emessa extends StatoMulta {

    private static final Emessa INSTANCE = new Emessa();

    private Emessa() {}

    static Emessa getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "EMESSA";
    }

    @Override
    StatoMulta onPay() {
        return Pagata.getInstance();
    }

    @Override
    StatoMulta onCancel() {
        return Annullata.getInstance();
    }

    @Override
    StatoMulta onExpire() {
        return Scaduta.getInstance();
    }
}
