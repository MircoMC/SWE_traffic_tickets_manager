package fines.speedcamera;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Finto servizio esterno di configurazione degli autovelox, in memoria. Parla il SUO formato (testi, nomi di
 * campo suoi, nessun ordine garantito): è l'SpeedCameraMapAdapter a tradurlo nei nostri tipi.
 * In seguito qui si parlerà con il servizio reale.
 *
 * REGOLE DEL SERVIZIO (le stesse che chiediamo al servizio vero): la storia è append-only verso il futuro.
 * {@link #schedule} accetta solo inizi nel FUTURO, validi e non già presenti: il passato non si modifica
 * e una riga non valida non può avvelenare la storia. {@link #load} è il canale del caricamento iniziale dei
 * dati (migrazione, test, o un cambio fatto fuori dal nostro sistema) e NON fa parte di ciò che il servizio
 * vero offre ai client: lo chiama solo il codice di assemblaggio.
 */
public class ExternalMapper {

    private final Map<String, List<Map<String, String>>> rows = new ConcurrentHashMap<>();
    private final Clock clock;

    public ExternalMapper() {
        this(Clock.systemUTC());
    }

    public ExternalMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Da "dal" (istante ISO-8601, es. 2026-01-01T00:00:00Z) l'autovelox ha questo limite e questa posizione.
     *
     * @throws IllegalArgumentException se "dal" non è un istante valido o non è nel futuro
     * @throws IllegalStateException    se esiste già una riga con lo stesso inizio
     */
    public void schedule(String cameraId, String from, String limitKmh, String lat, String lon, String address) {
        Instant start = instant(from);
        if (start == null) {
            throw new IllegalArgumentException("Inizio non valido: " + from);
        }
        if (!start.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Il passato non si modifica: l'inizio deve essere futuro");
        }
        List<Map<String, String>> list = rows.computeIfAbsent(cameraId, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            for (Map<String, String> existing : list) {
                if (start.equals(instant(existing.get("dal")))) {
                    throw new IllegalStateException("Esiste già una configurazione con questo inizio");
                }
            }
            list.add(row(from, limitKmh, lat, lon, address));
        }
    }

    /**
     * Caricamento iniziale dei dati, senza nessun controllo: scrive anche nel passato. Non è un'operazione
     * del servizio reale (vedi la nota della classe): in questo progetto la chiama solo il codice di assemblaggio.
     */
    public void load(String cameraId, String from, String limitKmh, String lat, String lon, String address) {
        rows.computeIfAbsent(cameraId, k -> new CopyOnWriteArrayList<>())
             .add(row(from, limitKmh, lat, lon, address));
    }

    /** Le righe dell'autovelox, nell'ordine in cui sono arrivate (vuoto se sconosciuto). */
    public List<Map<String, String>> read(String cameraId) {
        List<Map<String, String>> found = rows.get(cameraId);
        if (found == null) {
            return Collections.emptyList();
        }
        List<Map<String, String>> copy = new ArrayList<>();
        for (Map<String, String> r : found) {
            copy.add(Collections.unmodifiableMap(new HashMap<>(r)));
        }
        return copy;
    }

    private static Map<String, String> row(String from, String limitKmh, String lat, String lon, String address) {
        Map<String, String> row = new HashMap<>();
        row.put("dal", from);
        row.put("limite_kmh", limitKmh);
        row.put("lat", lat);
        row.put("lon", lon);
        row.put("indirizzo", address);
        return row;
    }

    private static Instant instant(String text) {
        try {
            return text == null ? null : Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
