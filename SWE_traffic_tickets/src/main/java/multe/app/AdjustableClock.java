package fines.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock che parte dall'istante corrente e si sposta in avanti a comando: per demo e test sulle scadenze. */
public final class AdjustableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override public ZoneId getZone()            { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant()           { return now; }
}
