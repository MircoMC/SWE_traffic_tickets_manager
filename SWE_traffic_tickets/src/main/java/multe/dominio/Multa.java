package multe.dominio;

import multe.osservatori.Observable;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Dato della multa e soggetto del pattern Observer.
 * Non gestisce le transizioni di stato: le fa lo StateManager.
 *
 * L'originale non esce mai dal package. Gli osservatori esterni e chi chiama la facciata
 * ricevono solo copie (vedi OsservatoreProtetto e ServiziMulte.getCopia).
 */
public class Multa extends Observable {

    private final int id;
    private final long amountCents;
    private final int idIntestatario;    // proprietario della targa ALL'ISTANTE della rilevazione
    private final LocalDate dueDate;
    private final Rilevazione rilevazione;   // autovelox, targa, velocità, limite e istante della misura
    private volatile StatoMulta state;   // volatile: letto anche fuori dai synchronized dello StateManager
    private volatile Provvedimento provvedimento;   // presente solo se la multa è stata annullata

    /** Monitor privato: lo StateManager serializza le operazioni su una multa con questo, non con la Multa stessa. */
    final Object lock = new Object();

    Multa(int id, long amountCents, int idIntestatario,
          LocalDate dueDate, Rilevazione rilevazione, StatoMulta state) {
        this(id, amountCents, idIntestatario, dueDate, rilevazione, state, null);
    }

    private Multa(int id, long amountCents, int idIntestatario,
                  LocalDate dueDate, Rilevazione rilevazione, StatoMulta state, Provvedimento provvedimento) {
        this.id = id;
        this.amountCents = amountCents;
        this.idIntestatario = idIntestatario;
        this.dueDate = dueDate;
        this.rilevazione = rilevazione;
        this.state = state;
        this.provvedimento = provvedimento;
    }

    public int getId()              { return id; }
    public long getAmountCents()    { return amountCents; }
    public String getPlate()        { return rilevazione.getTarga(); }
    public int getIdIntestatario()  { return idIntestatario; }
    public LocalDate getDueDate()   { return dueDate; }
    public int getDetectedSpeed()   { return rilevazione.getVelocita(); }
    public Rilevazione getRilevazione() { return rilevazione; }
    public StatoMulta getState()    { return state; }

    /** Chi ha annullato la multa, quando e perché (vuoto se non è annullata). */
    public Optional<Provvedimento> getProvvedimento() {
        return Optional.ofNullable(provvedimento);
    }

    /** Copia indipendente (senza osservatori), usata dal DAO e dagli osservatori per non condividere l'oggetto. */
    public Multa copia() {
        return new Multa(id, amountCents, idIntestatario, dueDate, rilevazione, state, provvedimento);
    }

    /** Come lo sarebbe la multa dopo la transizione: serve a salvarla nel DAO PRIMA di cambiare quella vera. */
    Multa copiaCon(StatoMulta nuovoStato, Provvedimento nuovoProvvedimento) {
        return new Multa(id, amountCents, idIntestatario, dueDate, rilevazione, nuovoStato,
            nuovoProvvedimento != null ? nuovoProvvedimento : provvedimento);
    }

    /**
     * Cambia stato e avvisa gli osservatori. Visibile solo al package: lo chiama
     * lo StateManager, così nessuno può forzare uno stato saltando le regole di transizione.
     */
    void changeState(StatoMulta newState, Evento evento, Provvedimento nuovoProvvedimento) {
        if (nuovoProvvedimento != null) {
            this.provvedimento = nuovoProvvedimento;
        }
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
