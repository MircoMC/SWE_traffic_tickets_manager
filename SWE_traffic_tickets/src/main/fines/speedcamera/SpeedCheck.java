package fines.speedcamera;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import fines.domain.Detection;

/**
 * La classe intermedia: riceve la misura di un autovelox e decide se è un'infrazione usando la configurazione
 * (limite e posizione) che il mapper dà PER QUELL'AUTOVELOX IN QUELL'ISTANTE. Non contiene nessuna soglia:
 * il limite sta solo nel mapper. Se c'è infrazione consegna la Detection al dominio
 * (in pratica {@code fineService::createFine}).
 */
public class SpeedCheck {

    private final SpeedCameraMap map;
    private final Function<Detection, UUID> recipient;

    public SpeedCheck(SpeedCameraMap map, Function<Detection, UUID> recipient) {
        this.map = Objects.requireNonNull(map);
        this.recipient = Objects.requireNonNull(recipient);
    }

    /**
     * @return l'id della multa creata, vuoto se non c'è infrazione (margine di misura di legge incluso)
     * @throws ConfigurationUnavailableException se per quell'istante non si conosce la configurazione
     */
    public Optional<UUID> evaluate(Measurement measurement) {
        List<Period> history = map.history(measurement.getCameraId());
        Configuration configuration = Period.configurationAt(history, measurement.getDetectedAt())
            .orElseThrow(() -> new ConfigurationUnavailableException(
                "Nessuna configurazione per l'autovelox " + measurement.getCameraId()
                + " all'istante " + measurement.getDetectedAt()));

        Detection detection = new Detection(measurement.getCameraId(), measurement.getPlate(), measurement.getSpeed(),
            configuration.getLimit(), measurement.getDetectedAt(), configuration.getLocation());
        if (!detection.isViolation()) {
            return Optional.empty();
        }
        return Optional.of(recipient.apply(detection));
    }
}
