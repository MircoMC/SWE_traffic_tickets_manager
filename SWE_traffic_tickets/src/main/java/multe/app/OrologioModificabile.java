package multe.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock che parte dall'istante corrente e si sposta in avanti a comando: per demo e test sulle scadenze. */
public final class OrologioModificabile extends Clock {

    private volatile Instant adesso = Instant.now();

    public void avanza(Duration durata) {
        adesso = adesso.plus(durata);
    }

    @Override public ZoneId getZone()            { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant()           { return adesso; }
}
