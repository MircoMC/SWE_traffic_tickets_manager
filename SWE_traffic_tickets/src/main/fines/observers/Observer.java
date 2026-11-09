package fines.observers;

/**
 * Observer: riceve l'avviso quando una Observable cambia.
 * Per le multe, source è la Fine e arg è un FineEvent (vedi FineObserver).
 */
public interface Observer {
    void update(Observable source, Object arg);
}
