package fines.domain;

/** Stato finale: pay e cancel sono rifiutate (una multa pagata non è annullabile), expire non ha effetto. */
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
