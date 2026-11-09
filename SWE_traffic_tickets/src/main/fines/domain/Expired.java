package fines.domain;

/** Expired ma ancora pagabile (con un costo maggiore, in futuro) e annullabile. */
final class Expired extends FineState {

    private static final Expired INSTANCE = new Expired();

    private Expired() {}

    static Expired getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "EXPIRED";
    }

    @Override
    FineState onPay() {
        return Paid.getInstance();
    }

    @Override
    FineState onCancel() {
        return Cancelled.getInstance();
    }
}
