package fines.domain;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import fines.observers.Observable;
import fines.observers.Observer;

/**
 * Sta tra la multa e gli osservatori registrati da fuori. Fa tre cose:
 * 1) consegna all'osservatore una COPIA della multa, mai l'originale (che resterebbe
 *    così manipolabile: deleteObservers, lock sul monitor, ...);
 * 2) la copia viene presa subito, dentro il lock della multa (fotografa lo stato dell'evento), ma la CONSEGNA
 *    avviene su un thread dedicato, in coda e in ordine, FUORI dal lock: un osservatore lento o che
 *    richiama la facciata non può bloccare né mandare in deadlock le operazioni sulle multe;
 * 3) isola gli errori: se un osservatore lancia un'eccezione, gli altri vengono avvisati lo stesso
 *    e l'operazione sulla multa non fallisce.
 * Un osservatore che non ritorna mai blocca solo le notifiche (la coda è unica), non il dominio.
 */
final class ProtectedObserver implements Observer {

    private final Observer delegate;
    private final Executor executor;

    ProtectedObserver(Observer delegate, Executor executor) {
        this.delegate = Objects.requireNonNull(delegate);
        this.executor = Objects.requireNonNull(executor);
    }

    @Override
    public void update(Observable source, Object arg) {
        Fine copy = ((Fine) source).copy();   // adesso, mentre siamo ancora dentro il lock
        try {
            executor.execute(() -> deliver(copy, arg));
        } catch (RejectedExecutionException e) {
            System.err.println("Notifica a " + delegate.getClass().getName() + " scartata: " + e);
        }
    }

    private void deliver(Fine copy, Object arg) {
        try {
            delegate.update(copy, arg);
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
