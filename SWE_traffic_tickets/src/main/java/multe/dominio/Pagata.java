package multe.dominio;

/** Stato finale: pay e cancel sono rifiutate (una multa pagata non è annullabile), expire non ha effetto. */
final class Pagata extends StatoMulta {

    private static final Pagata INSTANCE = new Pagata();

    private Pagata() {}

    static Pagata getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "PAGATA";
    }
}
