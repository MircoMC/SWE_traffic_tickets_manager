package fines.domain;

import java.time.LocalDate;

/** Record of a cancellation: who ordered it, why and when. Immutable. */
public final class CancellationRuling {

    private final int judgeId;
    private final String reason;
    private final LocalDate date;

    CancellationRuling(int judgeId, String reason, LocalDate date) {
        this.judgeId = judgeId;
        this.reason = reason;
        this.date = date;
    }

    public int getJudgeId()      { return judgeId; }
    public String getReason() { return reason; }
    public LocalDate getDate()     { return date; }
}
