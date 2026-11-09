package fines.domain;

import java.util.UUID;

/**
 * Capability per annullare le multe, parallela al pagamento: l'unico modo per annullare è avere
 * in mano questo oggetto, e lo si ottiene solo dal login di un Judge (vedi LoginProxy).
 * Il costruttore è package-private: nasce solo da FineService.cancellationFor.
 *
 * LIMITE NOTO: non c'è revoca. Finché la sessione esiste l'oggetto funziona anche se il ruolo del giudice
 * viene tolto dopo il login; se in futuro si aggiungono logout o scadenza, qui va un controllo di validità.
 */
public final class Cancellation {

    private final StateManager stateManager;
    private final int judgeId;

    Cancellation(StateManager stateManager, int judgeId) {
        this.stateManager = stateManager;
        this.judgeId = judgeId;
    }

    /**
     * @throws SecurityException        se la multa è intestata al giudice stesso
     * @throws IllegalStateException    se nello stato attuale la multa non è annullabile (es. già pagata)
     * @throws IllegalArgumentException se la multa non esiste o la motivazione è vuota
     */
    public void cancel(UUID fineId, String reason) {
        stateManager.cancelStarting(fineId, judgeId, reason);
    }
}
