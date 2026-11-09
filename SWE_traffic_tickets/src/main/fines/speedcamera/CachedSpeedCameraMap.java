package fines.speedcamera;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decoratore: tiene in memoria la storia di ogni autovelox e la rilegge dalla sorgente al massimo una volta
 * ogni {@code validita} (di default un giorno). Chi lo usa non sa se c'è una cache o no.
 *
 * SCELTA DI PROGETTO: la soglia cambia di rado, quindi un giorno di anzianità è accettato.
 * - Un cambio PROGRAMMATO (con data di inizio futura) è già nella storia in cache e scatta all'ora giusta.
 * - Un cambio IMMEDIATO e improvviso si vede solo al refresh successivo: va chiesto a mano con {@link #refresh()}.
 *
 * Se la sorgente non risponde si usa l'ultima copia, ma solo finché ha meno di {@code etaMassima}
 * (di default tre giorni): oltre, l'errore arriva al chiamante, perché giudicare con dati troppo vecchi
 * è peggio che aspettare. Un autovelox sconosciuto non viene memorizzato: appena viene registrato si vede.
 */
public final class CachedSpeedCameraMap implements SpeedCameraMap {

    private static final class Entry {
        final List<Period> history;
        final Instant loadedAt;

        Entry(List<Period> history, Instant loadedAt) {
            this.history = history;
            this.loadedAt = loadedAt;
        }
    }

    private final SpeedCameraMap source;
    private final Clock clock;
    private final Duration validity;
    private final Duration maxAge;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachedSpeedCameraMap(SpeedCameraMap source, Clock clock) {
        this(source, clock, Duration.ofDays(1), Duration.ofDays(3));
    }

    public CachedSpeedCameraMap(SpeedCameraMap source, Clock clock, Duration validity, Duration maxAge) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
        this.validity = Objects.requireNonNull(validity);
        this.maxAge = Objects.requireNonNull(maxAge);
    }

    @Override
    public List<Period> history(String cameraId) {
        Instant now = clock.instant();
        Entry entry = cache.get(cameraId);
        if (entry != null && Duration.between(entry.loadedAt, now).compareTo(validity) < 0) {
            return entry.history;
        }
        try {
            List<Period> fresh = source.history(cameraId);
            if (fresh.isEmpty()) {
                cache.remove(cameraId);          // sconosciuto: non si memorizza
            } else {
                cache.put(cameraId, new Entry(fresh, now));
            }
            return fresh;
        } catch (RuntimeException e) {
            if (entry != null && Duration.between(entry.loadedAt, now).compareTo(maxAge) < 0) {
                System.err.println("Mapper autovelox non raggiungibile, uso la copia del " + entry.loadedAt + ": " + e);
                return entry.history;
            }
            throw e;
        }
    }

    /** Scrive nella sorgente e invalida la copia di quell'autovelox: la modifica fatta da noi si vede subito. */
    @Override
    public void schedule(String cameraId, Instant from, Configuration configuration) {
        source.schedule(cameraId, from, configuration);
        cache.remove(cameraId);
    }

    /** Refresh manuale di tutti gli autovelox: la prossima richiesta rilegge dalla sorgente. */
    public void refresh() {
        cache.clear();
    }

    /** Refresh manuale di un solo autovelox. */
    public void refresh(String cameraId) {
        cache.remove(cameraId);
    }
}
