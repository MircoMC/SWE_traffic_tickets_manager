package fines.speedcamera;

import java.time.Instant;
import java.util.Objects;
import fines.plates.PlateRegistry;

/**
 * Ciò che un autovelox produce: un veicolo, una velocità, un istante. Niente limite, niente posizione:
 * l'autovelox non sa nulla della strada su cui sta, a deciderlo è il SpeedCheck con la configurazione
 * che il mapper dà per quell'autovelox in quell'istante.
 */
public final class Measurement {

    private final String cameraId;
    private final String plate;        // già normalizzata
    private final int speed;        // km/h
    private final Instant detectedAt;

    public Measurement(String cameraId, String plate, int speed, Instant detectedAt) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            throw new IllegalArgumentException("Autovelox mancante");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("La velocità rilevata deve essere positiva");
        }
        this.cameraId = cameraId;
        this.plate = PlateRegistry.normalize(plate);
        this.speed = speed;
        this.detectedAt = Objects.requireNonNull(detectedAt);
    }

    public String getCameraId() { return cameraId; }
    public String getPlate()       { return plate; }
    public int getSpeed()       { return speed; }
    public Instant getDetectedAt() { return detectedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Measurement)) return false;
        Measurement m = (Measurement) o;
        return speed == m.speed && cameraId.equals(m.cameraId)
            && plate.equals(m.plate) && detectedAt.equals(m.detectedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cameraId, plate, speed, detectedAt);
    }
}
