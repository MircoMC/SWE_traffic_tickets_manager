package fines.speedcamera;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Da questo istante (incluso) l'autovelox ha questa configurazione, fino al periodo successivo. */
public final class Period {

    private final Instant from;
    private final Configuration configuration;

    public Period(Instant from, Configuration configuration) {
        this.from = Objects.requireNonNull(from);
        this.configuration = Objects.requireNonNull(configuration);
    }

    public Instant getFrom()                    { return from; }
    public Configuration getConfiguration()  { return configuration; }

    /**
     * La configurazione in vigore all'istante dato, in una storia ordinata per inizio crescente
     * (come la restituisce SpeedCameraMap). Vuota se l'istante precede il primo periodo.
     */
    public static Optional<Configuration> configurationAt(List<Period> history, Instant instant) {
        Configuration valid = null;
        for (Period p : history) {
            if (p.from.isAfter(instant)) {
                break;
            }
            valid = p.configuration;
        }
        return Optional.ofNullable(valid);
    }
}
