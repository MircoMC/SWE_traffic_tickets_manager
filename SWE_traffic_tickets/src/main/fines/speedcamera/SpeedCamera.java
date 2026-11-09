package fines.speedcamera;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Un autovelox è un sensore generico: ha un id, misura e consegna la misura a chi deve giudicarla
 * (in pratica {@code controllo::valuta}). Non ha né limite né posizione né sa se c'è un'infrazione:
 * la stessa classe serve per qualsiasi strada, il comportamento dipende solo dalla configurazione nel mapper.
 */
public class SpeedCamera {

    private final String id;
    private final Function<Measurement, Optional<UUID>> recipient;
    private final Clock clock;

    public SpeedCamera(String id, Function<Measurement, Optional<UUID>> recipient, Clock clock) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Autovelox mancante");
        }
        this.id = id;
        this.recipient = Objects.requireNonNull(recipient);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Measurement un veicolo all'istante del Clock.
     *
     * @return l'id della multa creata, vuoto se non è infrazione
     */
    public Optional<UUID> detect(String plate, int speed) {
        return recipient.apply(new Measurement(id, plate, speed, clock.instant()));
    }

    public String getId() { return id; }
}
