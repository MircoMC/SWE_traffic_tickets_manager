public class Annullata implements StatoMulta {

    private static final Annullata INSTANCE = new Annullata();

    private Annullata() {}

    public static Annullata getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() { // <-- deve chiamarsi getState()
        return "ANNULLATA";
    }
}