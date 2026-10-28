package multe.dominio;

import multe.osservatori.Observable;
import multe.osservatori.Observer;

/** Comodità: fa il cast di source e arg, così le sottoclassi lavorano con tipi precisi. */
public abstract class MultaObserver implements Observer {

    @Override
    public final void update(Observable source, Object arg) {
        onEvento((Multa) source, (Evento) arg);
    }

    protected abstract void onEvento(Multa multa, Evento evento);
}
