package fines.observers;

/**
 * Observer: riceve l'avviso quando una Observable cambia.
 * Per le multe, source è la Multa e arg è un Evento (vedi FineObserver).
 */
public interface Observer {
    void update(Observable source, Object arg);
}
