package multe.storico;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import multe.dominio.Annullamento;
import multe.dominio.Multa;
import multe.utenti.Utente;

/**
 * Ciò che un utente ottiene dal login: solo gli strumenti che il suo ruolo gli consente.
 * Il costruttore è package-private: la crea solo il LoginProxy.
 */
public final class Sessione {

    private final Utente utente;
    private final StoricoMulte storico;
    private final Annullamento annullamento;   // null se l'utente non è un giudice
    private final PagamentoOnline pagamento;   // null se il LoginProxy non ha i servizi

    Sessione(Utente utente, StoricoMulte storico, Annullamento annullamento, PagamentoOnline pagamento) {
        this.utente = Objects.requireNonNull(utente);
        this.storico = Objects.requireNonNull(storico);
        this.annullamento = annullamento;
        this.pagamento = pagamento;
    }

    public Utente getUtente()        { return utente; }
    public StoricoMulte getStorico() { return storico; }

    /** Le multe di chi ha fatto il login: non serve indicare nessun id. */
    public List<Multa> getMieMulte() {
        return storico.getMulte(utente.getId());
    }

    /** Presente solo per i giudici. */
    public Optional<Annullamento> getAnnullamento() {
        return Optional.ofNullable(annullamento);
    }

    /**
     * Paga la multa con l'id scelto dalla lista (vedi PagamentoOnline per la scelta sui controlli).
     *
     * @return true se pagata, false se il gestore dei pagamenti ha rifiutato
     * @throws IllegalStateException se la multa non è pagabile o la sessione non ha accesso ai servizi
     */
    public boolean paga(int idMulta) {
        if (pagamento == null) {
            throw new IllegalStateException("Pagamento non disponibile in questa sessione");
        }
        return pagamento.paga(idMulta);
    }
}
