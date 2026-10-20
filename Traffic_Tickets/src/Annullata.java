/** Stato finale: pay e cancel sono rifiutate, expire non ha effetto. */
public final class Annullata implements StatoMulta {

    private static final Annullata INSTANCE = new Annullata();

    private Annullata() {}

    public static Annullata getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "ANNULLATA";
    }
}
