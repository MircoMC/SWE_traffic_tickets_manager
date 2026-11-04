package fines.speedcamera;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import fines.domain.Detection;

/**
 * Un autovelox misura la velocità e, se c'è infrazione (vedi Rilevazione.isViolation), consegna una Rilevazione
 * al dominio (in pratica {@code servizi::createFine}). Non conosce multe, stati né proprietari.
 * Il limite in vigore viene fotografato dentro ogni Rilevazione: cambiarlo poi non tocca le multe già emesse.
 */
public class SpeedCamera {

    private final String id;
    private final Location location;
    private final Function<Detection,UUID> recipient;
    private final Clock clock;
    private volatile int limit;     // km/h

    public SpeedCamera(String id, int limit, Location location,
                     Function<Detection,UUID> recipient, Clock clock) {
        this.id = Objects.requireNonNull(id);
        this.location = Objects.requireNonNull(location);
        this.recipient = Objects.requireNonNull(recipient);
        this.clock = Objects.requireNonNull(clock);
        validateLimit(limit);
        this.limit = limit;
    }

    /**
     * Misura un veicolo all'istante del Clock.
     *
     * @return l'id della multa creata, vuoto se non è infrazione (margine di misura di legge incluso)
     */
    public Optional<UUID> detect(String plate, int speed) {
        Instant now = clock.instant();
        Detection detection = new Detection(id, plate, speed, limit, now);
        if (!detection.isViolation()) {
            return Optional.empty();
        }
        return Optional.of(recipient.apply(detection));
    }
    /**
     * TODO permessi: dovrebbe poterlo fare solo un amministratore, ma oggi non c'è nessun controllo
     * (chi ha il riferimento all'Autovelox può cambiarlo). Da proteggere con una capability data dal login,
     * come per l'Annullamento, prima di esporre gli autovelox a dei client.
     */
    public void setLimit(int limit) {
        validateLimit(limit);
        this.limit = limit;
    }

    private static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Il limite di velocità deve essere positivo");
        }
    }

    public String getId()         { return id; }
    public int getLimit()        { return limit; }
    public Location getLocation() { return location; }
}
