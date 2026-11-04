package fines.domain;

import java.util.UUID;

/**
 * Capability per annullare le multe, parallela al pagamento: l'unico modo per annullare è avere
 * in mano questo oggetto, e lo si ottiene solo dal login di un Giudice (vedi LoginProxy).
 * Il costruttore è package-private: nasce solo da FineService.cancellationFor.
 */
public final class Cancellation {

    private final StateManager stateManager;
    private final int judgeId;

    Cancellation(StateManager stateManager, int judgeId) {
        this.stateManager = stateManager;
        this.judgeId = judgeId;
    }

    /**
     * @throws IllegalStateException    se nello stato attuale la multa non è annullabile (es. già pagata)
     * @throws IllegalArgumentException se la multa non esiste o la motivazione è vuota
     */
    public void cancel(UUID fineId, String reason) {
        stateManager.cancelStarting(fineId, judgeId, reason);
    }
}
