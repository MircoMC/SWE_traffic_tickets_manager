package multe.dominio;

/** Stato finale: pay e cancel sono rifiutate, expire non ha effetto. */
final class Annullata extends StatoMulta {

    private static final Annullata INSTANCE = new Annullata();

    private Annullata() {}

    static Annullata getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "ANNULLATA";
    }
}
