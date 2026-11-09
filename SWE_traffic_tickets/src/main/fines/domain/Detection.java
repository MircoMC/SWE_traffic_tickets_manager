package fines.domain;

import fines.speedcamera.Location;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import fines.plates.PlateRegistry;

/**
 * Value object: una misura fatta da un autovelox. Porta con sé tutto ciò che serve a decidere la multa:
 * il limite IN VIGORE al momento della misura (se poi l'autovelox viene riconfigurato la multa non cambia)
 * e l'istante, che serve a trovare chi era il proprietario della targa in quel momento.
 */
public final class Detection {

    /** Margine per gli errori di misura: sotto i 100 km/h si tolgono 5 km/h dalla velocità rilevata... */
    static final int TOLERANCE_KMH = 5;
    /** ...da 100 km/h in su si toglie il 5% (a 100 km/h i due margini coincidono: 5 km/h). */
    static final int PERCENTAGE_THRESHOLD_KMH = 100;

    private final String cameraId;
    private final String plate;          // già normalizzata
    private final int speed;          // km/h
    private final int limit;            // km/h
    private final Instant detectedAt;
    private final Location location;    // dove si trovava l'autovelox in quel momento (può mancare)

    public Detection(String cameraId, String plate, int speed, int limit, Instant detectedAt) {
        this(cameraId, plate, speed, limit, detectedAt, null);
    }

    public Detection(String cameraId, String plate, int speed, int limit, Instant detectedAt,
                       Location location) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            throw new IllegalArgumentException("Autovelox mancante");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("La velocità rilevata deve essere positiva");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Il limite di velocità deve essere positivo");
        }
        this.cameraId = cameraId;
        this.plate = PlateRegistry.normalize(plate);
        this.speed = speed;
        this.limit = limit;
        this.detectedAt = Objects.requireNonNull(detectedAt);
        this.location = location;
    }

    public String getCameraId() { return cameraId; }
    public String getPlate()       { return plate; }
    public int getSpeed()       { return speed; }
    public int getLimit()         { return limit; }
    public Instant getDetectedAt() { return detectedAt; }

    /** La posizione dell'autovelox al momento della misura (un autovelox mobile ne ha una diversa a ogni misura). */
    public Optional<Location> getLocation() { return Optional.ofNullable(location); }

    /**
     * La multa scatta solo se il limite è superato anche togliendo il margine di errore di misura:
     * sotto i 100 km/h velocità - 5 &gt; limite; da 100 km/h in su velocità * 95% &gt; limite.
     * Esempio con limite 50: 55 km/h non è infrazione, 56 sì.
     * La percentuale si confronta senza divisioni (velocità * 95 &gt; limite * 100) per non troncare i decimali.
     */
    public boolean isViolation() {
        if (speed < PERCENTAGE_THRESHOLD_KMH) {
            return speed - TOLERANCE_KMH > limit;
        }
        return (long) speed * 95 > (long) limit * 100;
    }

    /**
     * Km/h oltre il limite calcolati sulla velocità RILEVATA (0 se entro il limite), senza il margine di
     * misura: può essere positivo anche quando isViolation() è false. Serve solo per l'importo.
     */
    public int getExcess() { return Math.max(0, speed - limit); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Detection)) return false;
        Detection r = (Detection) o;
        return speed == r.speed && limit == r.limit && cameraId.equals(r.cameraId)
            && plate.equals(r.plate) && detectedAt.equals(r.detectedAt) && Objects.equals(location, r.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cameraId, plate, speed, limit, detectedAt, location);
    }

    @Override
    public String toString() {
        return "Rilevazione[" + cameraId + " " + plate + " " + speed + "/" + limit + " km/h @ " + detectedAt + "]";
    }
}
