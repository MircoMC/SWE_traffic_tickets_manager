package fines.domain;

import java.util.Objects;
import fines.observers.Observable;
import fines.observers.Observer;

/**
 * Sta tra la multa e gli osservatori registrati da fuori. Fa due cose:
 * 1) consegna all'osservatore una COPIA della multa, mai l'originale (che resterebbe
 *    così manipolabile: deleteObservers, lock sul monitor, ...);
 * 2) isola gli errori: se un osservatore lancia un'eccezione, gli altri vengono avvisati lo stesso
 *    e l'operazione sulla multa non fallisce.
 */
final class ProtectedObserver implements Observer {

    private final Observer delegate;

    ProtectedObserver(Observer delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void update(Observable source, Object arg) {
        try {
            delegate.update(((Fine) source).copy(), arg);
        } catch (RuntimeException e) {
            System.err.println("Osservatore " + delegate.getClass().getName()
                + " ha lanciato un'eccezione (ignorata): " + e);
        }
    }

    // stessa regola di Observable.addObserver: lo stesso osservatore non si registra due volte
    @Override
    public boolean equals(Object o) {
        return o instanceof ProtectedObserver && delegate.equals(((ProtectedObserver) o).delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
