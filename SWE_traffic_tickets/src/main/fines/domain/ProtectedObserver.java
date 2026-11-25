package fines.domain;

import fines.observers.Observable;
import fines.observers.Observer;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sits between the fine and the observers registered from outside. It does three things:
 * 1) hands the observer a COPY of the fine, never the original (which would stay
 *    manipulable: deleteObservers, locking the monitor, ...);
 * 2) isolates errors: if an observer throws an exception, the others are notified anyway
 *    and the operation on the fine does not fail;
 * 3) with an Executor, the delivery happens on another thread: a slow observer never holds the lock of the fine
 *    (the copy is made on the calling thread, so it shows the fine exactly as it was at that moment).
 */
final class ProtectedObserver implements Observer {

    private final Observer delegate;
    private final Executor executor;   // null: delivery on the calling thread

    ProtectedObserver(Observer delegate) {
        this(delegate, null);
    }

    ProtectedObserver(Observer delegate, Executor executor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.executor = executor;
    }

    @Override
    public void update(Observable source, Object arg) {
        Fine copy = ((Fine) source).copy();
        if (executor == null) {
            deliver(copy, arg);
            return;
        }
        try {
            executor.execute(() -> deliver(copy, arg));
        } catch (RejectedExecutionException e) {
            System.err.println("Observer " + delegate.getClass().getName() + ": notification dropped (" + e + ")");
        }
    }

    private void deliver(Fine copy, Object arg) {
        try {
            delegate.update(copy, arg);
        } catch (RuntimeException e) {
            System.err.println("Observer " + delegate.getClass().getName() + " threw an exception (ignored): " + e);
        }
    }

    // same rule as Observable.addObserver: the same observer is not registered twice
    @Override
    public boolean equals(Object o) {
        return o instanceof ProtectedObserver && delegate.equals(((ProtectedObserver) o).delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
