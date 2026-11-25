package fines.domain;

/** Final state: pay and cancel are rejected, expire has no effect. */
final class Cancelled extends FineState {

    private static final Cancelled INSTANCE = new Cancelled();

    private Cancelled() {}

    static Cancelled getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "CANCELLED";
    }
}
