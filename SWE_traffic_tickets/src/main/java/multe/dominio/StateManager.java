package multe.dominio;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sta tra Multa e StatoMulta: è l'unico che cambia lo stato di una multa.
 * Lavora sempre per id e recupera l'oggetto originale dalla Identity Map (MapperId).
 *
 * Ogni operazione segue lo stesso schema: (1) lo stato corrente dice quale sarà il successivo
 * e rifiuta se l'operazione non è consentita, (2) si esegue l'azione esterna (addebito),
 * (3) si SALVA nel DAO la multa aggiornata, (4) solo se il salvataggio riesce si cambia la multa
 * in memoria e si avvisano gli osservatori. Così la memoria non è mai "più avanti" del database.
 *
 * Concorrenza: operazioni su multe diverse procedono in parallelo, su una stessa multa sono
 * serializzate dal monitor privato Multa.lock. Gli osservatori girano dentro il lock (ricevono
 * copie e gli errori sono isolati); se diventeranno lenti andranno spostati su una coda.
 */
class StateManager {

    private final MapperId mapperId;
    private final GatewayPagamento pagamenti;
    private final MultaDAO multaDAO;
    private final Clock clock;

    StateManager(MapperId mapperId, GatewayPagamento pagamenti, MultaDAO multaDAO, Clock clock) {
        this.mapperId = mapperId;
        this.pagamenti = pagamenti;
        this.multaDAO = multaDAO;
        this.clock = clock;
    }

    StatoMulta getState(int idMulta) {
        Multa multa = trova(idMulta);
        synchronized (multa.lock) {
            return multa.getState();
        }
    }

    /**
     * Avvia il pagamento: se il gestore risponde true la multa passa a Pagata.
     * Se il salvataggio fallisce DOPO l'addebito, l'eccezione arriva al chiamante e lo stato resta
     * invariato: ripetere paga() completa la transizione senza addebitare di nuovo
     * (il GatewayPagamento è idempotente per id).
     *
     * @return true se pagata, false se il gestore ha rifiutato (stato invariato)
     * @throws IllegalStateException se nello stato attuale la multa non è pagabile
     */
    boolean paymentStarting(int idMulta) {
        Multa multa = trova(idMulta);
        synchronized (multa.lock) {
            StatoMulta prossimo = multa.getState().onPay();   // rifiuta PRIMA di addebitare
            if (!pagamenti.payment(multa.getId(), multa.getAmountCents())) {
                return false;
            }
            applica(multa, prossimo, Evento.PAGATA, null);
            return true;
        }
    }

    /** Annullamento disposto da un giudice. Una multa pagata non è annullabile. */
    void cancelStarting(int idMulta, int idGiudice, String motivazione) {
        if (motivazione == null || motivazione.trim().isEmpty()) {
            throw new IllegalArgumentException("Motivazione obbligatoria");
        }
        Multa multa = trova(idMulta);
        synchronized (multa.lock) {
            StatoMulta prossimo = multa.getState().onCancel();
            applica(multa, prossimo, Evento.ANNULLATA,
                new Provvedimento(idGiudice, motivazione.trim(), LocalDate.now(clock)));
        }
    }

    /** La data viene dal Clock del sistema, non dal chiamante. */
    void checkExpiration(int idMulta) {
        Multa multa = trova(idMulta);
        synchronized (multa.lock) {
            if (LocalDate.now(clock).isAfter(multa.getDueDate())) {
                StatoMulta attuale = multa.getState();
                StatoMulta prossimo = attuale.onExpire();
                if (prossimo != attuale) {
                    applica(multa, prossimo, Evento.SCADUTA, null);
                }
            }
        }
    }

    /** Sempre dentro un synchronized(multa.lock). Prima il DAO, poi la memoria, poi gli osservatori. */
    private void applica(Multa multa, StatoMulta prossimo, Evento evento, Provvedimento provvedimento) {
        if (mapperId.get(multa.getId()) != multa) {
            throw new IllegalArgumentException(
                "La multa " + multa.getId() + " non è quella registrata nel MapperId");
        }
        multaDAO.aggiorna(multa.copiaCon(prossimo, provvedimento));
        multa.changeState(prossimo, evento, provvedimento);
    }

    private Multa trova(int idMulta) {
        Multa multa = mapperId.get(idMulta);
        if (multa == null) {
            throw new IllegalArgumentException("Multa inesistente: " + idMulta);
        }
        return multa;
    }
}
