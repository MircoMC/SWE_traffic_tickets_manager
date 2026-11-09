package fines.speedcamera;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import fines.speedcamera.Location;

/**
 * Adapter: traduce il formato del servizio esterno (testi) nei nostri tipi e garantisce le regole che il
 * resto del sistema dà per scontate: storia ordinata, limiti positivi, coordinate valide, nessun inizio doppio.
 * Dati non validi non vengono corretti né ignorati: l'elaborazione fallisce in modo esplicito.
 */
public class SpeedCameraMapAdapter implements SpeedCameraMap {

    private final ExternalMapper external;

    public SpeedCameraMapAdapter(ExternalMapper external) {
        this.external = Objects.requireNonNull(external);
    }

    @Override
    public List<Period> history(String cameraId) {
        List<Period> periods = new ArrayList<>();
        for (Map<String, String> row : external.read(cameraId)) {
            periods.add(translate(cameraId, row));
        }
        periods.sort(Comparator.comparing(Period::getFrom));
        for (int i = 1; i < periods.size(); i++) {
            if (periods.get(i).getFrom().equals(periods.get(i - 1).getFrom())) {
                throw invalid(cameraId, "due configurazioni con lo stesso inizio");
            }
        }
        return Collections.unmodifiableList(periods);
    }

    @Override
    public void schedule(String cameraId, Instant from, Configuration configuration) {
        Objects.requireNonNull(configuration);
        Location location = configuration.getLocation();
        external.schedule(cameraId, Objects.requireNonNull(from).toString(),
            Integer.toString(configuration.getLimit()),
            Double.toString(location.getLatitude()), Double.toString(location.getLongitude()),
            location.getAddress());
    }

    private Period translate(String id, Map<String, String> row) {
        try {
            Instant from = Instant.parse(field(id, row, "dal"));
            int limit = Integer.parseInt(field(id, row, "limite_kmh").trim());
            double lat = Double.parseDouble(field(id, row, "lat").trim());
            double lon = Double.parseDouble(field(id, row, "lon").trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                throw invalid(id, "coordinate fuori intervallo");
            }
            Location location = new Location(field(id, row, "indirizzo"), lat, lon);
            return new Period(from, new Configuration(limit, location));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw invalid(id, "dati non validi (" + e.getMessage() + ")");
        }
    }

    private String field(String id, Map<String, String> row, String name) {
        String value = row.get(name);
        if (value == null) {
            throw invalid(id, "manca il campo " + name);
        }
        return value;
    }

    private IllegalStateException invalid(String id, String detail) {
        return new IllegalStateException("Configurazione non valida per l'autovelox " + id + ": " + detail);
    }
}
