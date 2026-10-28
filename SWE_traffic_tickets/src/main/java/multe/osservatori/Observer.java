package multe.osservatori;

/**
 * Observer: riceve l'avviso quando una Observable cambia.
 * Per le multe, source è la Multa e arg è un Evento (vedi MultaObserver).
 */
public interface Observer {
    void update(Observable source, Object arg);
}
