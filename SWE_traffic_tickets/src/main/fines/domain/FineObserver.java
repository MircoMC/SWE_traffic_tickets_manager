package fines.domain;

import fines.observers.Observable;
import fines.observers.Observer;

/** Convenience: casts source and arg, so subclasses work with precise types. */
public abstract class FineObserver implements Observer {

    @Override
    public final void update(Observable source, Object arg) {
        onEvent((Fine) source, (FineEvent) arg);
    }

    protected abstract void onEvent(Fine fine, FineEvent event);
}
