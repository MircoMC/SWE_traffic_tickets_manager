package fines.domain;

/** Final state: pay and cancel are rejected (a paid fine cannot be cancelled), expire has no effect. */
final class Paid extends FineState {

    private static final Paid INSTANCE = new Paid();

    private Paid() {}

    static Paid getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "PAID";
    }
}
