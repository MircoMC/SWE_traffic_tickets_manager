package fines.domain;

import java.util.UUID;

/**
 * Capability to cancel fines, parallel to payment: the only way to cancel is to hold
 * this object, which is obtained only from the login of a Judge (see LoginProxy).
 * The constructor is package-private: it is only created by FineService.cancellationFor.
 */
public final class Cancellation {

    private final StateManager stateManager;
    private final int judgeId;

    Cancellation(StateManager stateManager, int judgeId) {
        this.stateManager = stateManager;
        this.judgeId = judgeId;
    }

    /**
     * @throws IllegalStateException    if in the current state the fine cannot be cancelled (e.g. already paid)
     * @throws IllegalArgumentException if the fine does not exist or the reason is empty
     */
    public void cancel(UUID fineId, String reason) {
        stateManager.cancelStarting(fineId, judgeId, reason);
    }
}
