import java.time.LocalDate;

/**
 * Dato della multa e soggetto del pattern Observer.
 * Non gestisce più le transizioni di stato: le fa lo StateManager.
 */
public class Multa extends Observable {

    private final int id;
    private final long amountCents;
    private final String plate;
    private final LocalDate dueDate;
    private final int detectedSpeed;
    private volatile StatoMulta state;   // volatile: letto anche fuori dai metodi synchronized dello StateManager

    public Multa(int id, long amountCents, String plate, LocalDate dueDate,
                 int detectedSpeed, StatoMulta state) {
        this.id = id;
        this.amountCents = amountCents;
        this.plate = plate;
        this.dueDate = dueDate;
        this.detectedSpeed = detectedSpeed;
        this.state = state;
    }

    public int getId()              { return id; }
    public long getAmountCents()    { return amountCents; }
    public String getPlate()        { return plate; }
    public LocalDate getDueDate()   { return dueDate; }
    public int getDetectedSpeed()   { return detectedSpeed; }
    public StatoMulta getState()    { return state; }

    /** Copia indipendente (senza osservatori), usata dal DAO per non condividere l'oggetto. */
    public Multa copia() {
        return new Multa(id, amountCents, plate, dueDate, detectedSpeed, state);
    }

    /**
     * Cambia stato e avvisa gli osservatori. Visibile solo al package: lo chiama
     * lo StateManager, così nessuno può forzare uno stato saltando le regole di transizione.
     */
    void changeState(StatoMulta newState, Evento evento) {
        this.state = newState;
        setChanged();
        notifyObservers(evento);
    }

    /** Annuncia la nascita della multa agli osservatori già collegati. */
    void announceEmission() {
        setChanged();
        notifyObservers(Evento.EMESSA);
    }
}
