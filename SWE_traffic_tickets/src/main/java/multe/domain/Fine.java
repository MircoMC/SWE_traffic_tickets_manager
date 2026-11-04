package fines.domain;

import fines.observers.Observable;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Dato della multa e soggetto del pattern Observer.
 * Non gestisce le transizioni di stato: le fa lo StateManager.
 *
 * L'originale non esce mai dal package. Gli osservatori esterni e chi chiama la facciata
 * ricevono solo copie (vedi ProtectedObserver e FineService.getCopy).
 */
public class Fine extends Observable {

    private final UUID id;
    private final long amountCents;
    private final int ownerId;    // proprietario della targa ALL'ISTANTE della rilevazione
    private final LocalDate dueDate;
    private final Detection detection;   // autovelox, targa, velocità, limite e istante della misura
    private volatile FineState state;   // volatile: letto anche fuori dai synchronized dello StateManager
    private volatile CancellationRuling ruling;   // presente solo se la multa è stata annullata

    /**
     * Monitor privato: lo StateManager serializza le operazioni su una multa con questo, non con la Multa stessa.
     */
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

    public UUID getId() {
        return id;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getPlate() {
        return detection.getPlate();
    }

    public int getOwnerId() {
        return ownerId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getDetectedSpeed() {
        return detection.getSpeed();
    }

    public Detection getDetection() {
        return detection;
    }

    public FineState getState() {
        return state;
    }

    /**
     * Chi ha annullato la multa, quando e perché (vuoto se non è annullata).
     */
    public Optional<CancellationRuling> getRuling() {
        return Optional.ofNullable(ruling);
    }

    /**
     * Copia indipendente (senza osservatori), usata dal DAO e dagli osservatori per non condividere l'oggetto.
     */
    public Fine copy() {
        return new Fine(id, amountCents, ownerId, dueDate, detection, state, ruling);
    }

    /**
     * Come lo sarebbe la multa dopo la transizione: serve a salvarla nel DAO PRIMA di cambiare quella vera.
     */
    Fine copyWith(FineState newState, CancellationRuling newRuling) {
        return new Fine(id, amountCents, ownerId, dueDate, detection, newState,
                newRuling != null ? newRuling : ruling);
    }

    /**
     * Cambia stato e avvisa gli osservatori. Visibile solo al package: lo chiama
     * lo StateManager, così nessuno può forzare uno stato saltando le regole di transizione.
     */
    void changeState(FineState newState, FineEvent event, CancellationRuling newRuling) {
        if (newRuling != null) {
            this.ruling = newRuling;
        }
        this.state = newState;
        setChanged();
        notifyObservers(event);
    }

    /**
     * Annuncia la nascita della multa agli osservatori già collegati.
     */
    void announceEmission() {
        setChanged();
        notifyObservers(FineEvent.ISSUED);
    }

    public static Fine restore(UUID id, long amountCents, int ownerId, LocalDate dueDate,
                               Detection detection, String stateName,
                               Integer judgeId, String reason, LocalDate rulingDate) {

        FineState state = parseState(stateName);
        CancellationRuling ruling = (judgeId != null) ? new CancellationRuling(judgeId, reason, rulingDate) : null;

        return new Fine(id, amountCents, ownerId, dueDate, detection, state, ruling);
    }

    private static FineState parseState(String state) {
        switch (state) {
            case "ISSUED":
                return Issued.getInstance();
            case "PAID":
                return Paid.getInstance();
            case "EXPIRED":
                return Expired.getInstance();
            case "CANCELLED":
                return Cancelled.getInstance();
            default:
                throw new IllegalArgumentException("Stato sconosciuto: " + state);
        }
    }
}
