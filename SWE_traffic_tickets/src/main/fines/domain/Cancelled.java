package fines.domain;

/** AttemptState finale: pay e cancel sono rifiutate, expire non ha effetto. */
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
