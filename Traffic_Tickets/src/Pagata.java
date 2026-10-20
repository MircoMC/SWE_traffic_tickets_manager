/** Stato finale: pay e cancel sono rifiutate, expire non ha effetto. */
public final class Pagata implements StatoMulta {

    private static final Pagata INSTANCE = new Pagata();

    private Pagata() {}

    public static Pagata getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "PAGATA";
    }
}
