package fines.domain;

/**
 * State. Abstract class with package-private constructor and transition methods:
 * from outside the package you can only read the state name, nobody can
 * implement a new one or invoke the transitions.
 *
 * States are pure functions: they say WHICH state comes next, but do not
 * change anything. Only the StateManager performs the transition, so
 * PAID can only be reached through StateManager.paymentStarting.
 *
 * By default pay and cancel are rejected, expire has no effect
 * (an already paid or cancelled fine can stay past its due date without errors).
 */
public abstract class FineState {

    FineState() {}

    public abstract String getState();

    /** @return the state after payment; throws IllegalStateException if not allowed */
    FineState onPay() {
        throw rejected("pay");
    }

    /** @return the state after cancellation; throws IllegalStateException if not allowed */
    FineState onCancel() {
        throw rejected("cancel");
    }

    /** @return the state after expiration; this if expiration has no effect */
    FineState onExpire() {
        return this;
    }

    private IllegalStateException rejected(String operation) {
        return new IllegalStateException("Operation '" + operation + "' not allowed in state: " + getState());
    }

    /** Maps a stored state name back to the singleton state (used when loading from persistent storage). */
    static FineState fromName(String name) {
        switch (name) {
            case "ISSUED":    return Issued.getInstance();
            case "PAID":      return Paid.getInstance();
            case "EXPIRED":   return Expired.getInstance();
            case "CANCELLED": return Cancelled.getInstance();
            default: throw new IllegalArgumentException("Unknown fine state: " + name);
        }
    }

    @Override
    public String toString() {
        return getState();
    }
}
