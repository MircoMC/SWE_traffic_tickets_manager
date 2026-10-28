package multe.dominio;

import java.util.Objects;
import multe.osservatori.Observable;
import multe.osservatori.Observer;

/**
 * Sta tra la multa e gli osservatori registrati da fuori. Fa due cose:
 * 1) consegna all'osservatore una COPIA della multa, mai l'originale (che resterebbe
 *    così manipolabile: deleteObservers, lock sul monitor, ...);
 * 2) isola gli errori: se un osservatore lancia un'eccezione, gli altri vengono avvisati lo stesso
 *    e l'operazione sulla multa non fallisce.
 */
final class OsservatoreProtetto implements Observer {

    private final Observer delegato;

    OsservatoreProtetto(Observer delegato) {
        this.delegato = Objects.requireNonNull(delegato);
    }

    @Override
    public void update(Observable source, Object arg) {
        try {
            delegato.update(((Multa) source).copia(), arg);
        } catch (RuntimeException e) {
            System.err.println("Osservatore " + delegato.getClass().getName()
                + " ha lanciato un'eccezione (ignorata): " + e);
        }
    }

    // stessa regola di Observable.addObserver: lo stesso osservatore non si registra due volte
    @Override
    public boolean equals(Object o) {
        return o instanceof OsservatoreProtetto && delegato.equals(((OsservatoreProtetto) o).delegato);
    }

    @Override
    public int hashCode() {
        return delegato.hashCode();
    }
}
