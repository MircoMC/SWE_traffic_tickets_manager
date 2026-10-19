public class Pagata implements StatoMulta {

    private static final Pagata INSTANCE = new Pagata();

    private Pagata() {}

    public static Pagata getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() { // <-- deve chiamarsi getState()
        return "PAGATA";
    }
}