package fines.domain;

import java.time.LocalDate;

/** Traccia di un annullamento: chi l'ha disposto, perché e quando. Immutabile. */
public final class CancellationRuling {

    private final int judgeId;
    private final String reason;
    private final LocalDate data;

    CancellationRuling(int judgeId, String reason, LocalDate data) {
        this.judgeId = judgeId;
        this.reason = reason;
        this.data = data;
    }

    public int getJudgeId()      { return judgeId; }
    public String getReason() { return reason; }
    public LocalDate getDate()     { return data; }
}
