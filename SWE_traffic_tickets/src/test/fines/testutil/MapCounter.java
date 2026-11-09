package fines.testutil;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import fines.speedcamera.SpeedCameraMap;
import fines.speedcamera.Configuration;
import fines.speedcamera.Period;

/** Mapper che conta le letture dalla sorgente e può simulare un servizio non raggiungibile. */
public final class MapCounter implements SpeedCameraMap {

    private final SpeedCameraMap delegato;
    private final AtomicInteger chiamate = new AtomicInteger();
    private volatile boolean nonRaggiungibile;

    public MapCounter(SpeedCameraMap delegato) {
        this.delegato = delegato;
    }

    @Override
    public List<Period> history(String idAutovelox) {
        chiamate.incrementAndGet();
        if (nonRaggiungibile) {
            throw new IllegalStateException("Servizio mappe non raggiungibile");
        }
        return delegato.history(idAutovelox);
    }

    @Override
    public void schedule(String idAutovelox, Instant dal, Configuration configurazione) {
        delegato.schedule(idAutovelox, dal, configurazione);
    }

    public int chiamate()                     { return chiamate.get(); }
    public void nonRaggiungibile(boolean n)   { this.nonRaggiungibile = n; }
}
