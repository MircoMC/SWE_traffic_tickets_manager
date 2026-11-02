package fines.domain;

final class Issued extends FineState {

    private static final Issued INSTANCE = new Issued();

    private Issued() {}

    static Issued getInstance() {
        return INSTANCE;
    }

    @Override
    public String getState() {
        return "ISSUED";
    }

    @Override
    FineState onPay() {
        return Paid.getInstance();
    }

    @Override
    FineState onCancel() {
        return Cancelled.getInstance();
    }

    @Override
    FineState onExpire() {
        return Expired.getInstance();
    }
}
