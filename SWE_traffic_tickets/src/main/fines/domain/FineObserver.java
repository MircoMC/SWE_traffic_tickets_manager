package fines.domain;

import fines.observers.Observable;
import fines.observers.Observer;

/** Comodità: fa il cast di source e arg, così le sottoclassi lavorano con tipi precisi. */
public abstract class FineObserver implements Observer {

    @Override
    public final void update(Observable source, Object arg) {
        onEvent((Fine) source, (FineEvent) arg);
    }

    protected abstract void onEvent(Fine fine, FineEvent event);
}
