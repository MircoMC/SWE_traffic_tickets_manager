package multe.dominio;

import java.time.Clock;
import java.util.Objects;
import multe.osservatori.Observer;
import multe.targhe.RegistroTarghe;
import multe.utenti.Giudice;

/**
 * Facciata: l'unico punto d'ingresso pubblico al dominio delle multe.
 * MapperId, StateManager e MulteMaker sono interni al package e non si vedono da fuori:
 * dall'esterno si lavora solo per id e si ricevono solo copie.
 *
 * Questo oggetto è il nucleo FIDATO: lo hanno solo il codice di assemblaggio e il LoginProxy.
 * I client ricevono dal login soltanto ciò che il loro ruolo consente (storico, Annullamento).
 *
 * Il DAO viene aggiornato direttamente dallo StateManager a ogni transizione, non tramite osservatori.
 */
public final class ServiziMulte {

    private final MapperId mapperId = new MapperId();
    private final StateManager stateManager;
    private final MulteMaker maker;

    public ServiziMulte(MultaDAO multaDAO, RegistroTarghe registroTarghe, GatewayPagamento pagamenti) {
        this(multaDAO, registroTarghe, pagamenti, Clock.systemDefaultZone());
    }

    public ServiziMulte(MultaDAO multaDAO, RegistroTarghe registroTarghe, GatewayPagamento pagamenti, Clock clock) {
        Objects.requireNonNull(multaDAO);
        Objects.requireNonNull(clock);
        this.stateManager = new StateManager(mapperId, Objects.requireNonNull(pagamenti), multaDAO, clock);
        this.maker = new MulteMaker(mapperId, multaDAO, Objects.requireNonNull(registroTarghe), clock);
    }

    /**
     * Riceve una misura dell'autovelox e crea la multa. Lo dà come ToIntFunction all'Autovelox: {@code servizi::creaMulta}.
     *
     * @return l'id della nuova multa
     * @throws IllegalArgumentException    se la velocità non supera il limite
     * @throws TargaNonRegistrataException se la targa non aveva un proprietario all'istante della rilevazione
     */
    public int creaMulta(Rilevazione rilevazione) {
        return maker.createMulta(rilevazione);
    }

    /**
     * Può pagare chiunque: conta solo che la multa venga pagata. L'importo viene sempre dalla multa.
     *
     * @return true se pagata, false se il gestore dei pagamenti ha rifiutato
     * @throws IllegalStateException se nello stato attuale la multa non è pagabile
     */
    public boolean paga(int idMulta) {
        return stateManager.paymentStarting(idMulta);
    }

    /** Controlla la scadenza con la data del Clock: il chiamante non può sceglierla. */
    public void verificaScadenza(int idMulta) {
        stateManager.checkExpiration(idMulta);
    }

    /** L'annullamento si fa solo tramite l'oggetto Annullamento, che si ottiene qui per un Giudice. */
    public Annullamento annullamentoPer(Giudice giudice) {
        return new Annullamento(stateManager, Objects.requireNonNull(giudice).getId());
    }

    public StatoMulta getStato(int idMulta) {
        return stateManager.getState(idMulta);
    }

    /** Copia indipendente: modificarla non ha alcun effetto sulla multa vera. */
    public Multa getCopia(int idMulta) {
        Multa copia = mapperId.getCopia(idMulta);
        if (copia == null) {
            throw new IllegalArgumentException("Multa inesistente: " + idMulta);
        }
        return copia;
    }

    /**
     * L'osservatore riceverà emissione e cambi di stato di tutte le multe create da ora in poi,
     * sempre con una COPIA della multa. Un'eccezione dell'osservatore non ferma gli altri né l'operazione.
     */
    public void aggiungiOsservatore(Observer osservatore) {
        maker.addObserver(new OsservatoreProtetto(osservatore));
    }
}
