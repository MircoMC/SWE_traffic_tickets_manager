public interface StatoMulta {

    String getState();

    default void pay(Multa multa) {
        throw new IllegalStateException("Operation 'pay' not allowed in state: " + getState());
    }

    default void cancel(Multa multa) {
        throw new IllegalStateException("Operation 'cancel' not allowed in state: " + getState());
    }

    default void expire(Multa multa) {
        throw new IllegalStateException("Operation 'expire' not allowed in state: " + getState());
    }
}