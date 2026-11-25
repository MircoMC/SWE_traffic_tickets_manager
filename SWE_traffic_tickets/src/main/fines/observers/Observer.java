package fines.observers;

/**
 * Observer: receives the notification when an Observable changes.
 * For fines, source is the Fine and arg is a FineEvent (see FineObserver).
 */
public interface Observer {
    void update(Observable source, Object arg);
}
