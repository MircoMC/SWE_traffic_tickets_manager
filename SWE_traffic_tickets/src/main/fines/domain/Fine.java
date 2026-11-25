package fines.domain;

import fines.observers.Observable;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Fine data and subject of the Observer pattern.
 * It does not handle state transitions: the StateManager does.
 *
 * The original never leaves the package. External observers and whoever calls the facade
 * only receive copies (see ProtectedObserver and FineService.getCopy).
 */
public class Fine extends Observable {

    private final UUID id;
    private final long amountCents;
    private final int ownerId;    // owner of the plate AT THE INSTANT of the detection
    private final LocalDate dueDate;
    private final Detection detection;   // speed camera, plate, speed, limit and instant of the measurement
    private volatile FineState state;   // volatile: also read outside the StateManager's synchronized blocks
    private volatile CancellationRuling ruling;   // present only if the fine was cancelled

    /** Private monitor: the StateManager serializes the operations on a fine with this, not with the Fine itself. */
    final Object lock = new Object();

    Fine(UUID id, long amountCents, int ownerId,
          LocalDate dueDate, Detection detection, FineState state) {
        this(id, amountCents, ownerId, dueDate, detection, state, null);
    }

    private Fine(UUID iD, long amountCents, int ownerId,
                  LocalDate dueDate, Detection detection, FineState state, CancellationRuling ruling) {
        this.id = iD;
        this.amountCents = amountCents;
        this.ownerId = ownerId;
        this.dueDate = dueDate;
        this.detection = detection;
        this.state = state;
        this.ruling = ruling;
    }

    public UUID  getId()              { return id; }
    public long getAmountCents()    { return amountCents; }
    public String getPlate()        { return detection.getPlate(); }
    public int getOwnerId()  { return ownerId; }
    public LocalDate getDueDate()   { return dueDate; }
    public int getDetectedSpeed()   { return detection.getSpeed(); }
    public Detection getDetection() { return detection; }
    public FineState getState()    { return state; }

    /** Who cancelled the fine, when and why (empty if it is not cancelled). */
    public Optional<CancellationRuling> getRuling() {
        return Optional.ofNullable(ruling);
    }

    /**
     * Rebuilds a fine read from persistent storage. Meant for DAO implementations only:
     * the state is given by its name and the ruling by its fields (null judgeId = not cancelled).
     */
    public static Fine restore(UUID id, long amountCents, int ownerId, LocalDate dueDate, Detection detection,
                               String stateName, Integer judgeId, String reason, LocalDate rulingDate) {
        CancellationRuling ruling = judgeId == null ? null : new CancellationRuling(judgeId, reason, rulingDate);
        return new Fine(id, amountCents, ownerId, dueDate, detection, FineState.fromName(stateName), ruling);
    }

    /** Independent copy (without observers), used by the DAO and the observers so the object is not shared. */
    public Fine copy() {
        return new Fine(id, amountCents, ownerId, dueDate, detection, state, ruling);
    }

    /** What the fine would look like after the transition: used to save it in the DAO BEFORE changing the real one. */
    Fine copyWith(FineState newState, CancellationRuling newRuling) {
        return new Fine(id, amountCents, ownerId, dueDate, detection, newState,
            newRuling != null ? newRuling : ruling);
    }

    /**
     * Changes state and notifies the observers. Visible only to the package: it is called by
     * the StateManager, so nobody can force a state by skipping the transition rules.
     */
    void changeState(FineState newState, FineEvent event, CancellationRuling newRuling) {
        if (newRuling != null) {
            this.ruling = newRuling;
        }
        this.state = newState;
        setChanged();
        notifyObservers(event);
    }

    /** Announces the birth of the fine to the observers already attached. */
    void announceEmission() {
        setChanged();
        notifyObservers(FineEvent.ISSUED);
    }
}
