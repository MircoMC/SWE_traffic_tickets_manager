package fines.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import fines.speedcamera.Location;
import fines.plates.PlateRegistry;

/**
 * Value object: a measurement made by a speed camera. It carries everything needed to decide the fine:
 * the limit IN FORCE at the time of the measurement (if the speed camera is reconfigured later the fine does not change)
 * and the instant, used to find who owned the plate at that moment.
 */
public final class Detection {

    /** Margin for measurement errors: below 100 km/h, 5 km/h are subtracted from the detected speed... */
    static final int TOLERANCE_KMH = 5;
    /** ...from 100 km/h up, 5% is subtracted (at 100 km/h the two margins coincide: 5 km/h). */
    static final int PERCENTAGE_THRESHOLD_KMH = 100;

    private final String cameraId;
    private final String plate;          // already normalized
    private final int speed;          // km/h
    private final int limit;            // km/h
    private final Instant detectedAt;
    private final Location location;      // position of the camera when measuring (null if unknown)

    public Detection(String cameraId, String plate, int speed, int limit, Instant detectedAt) {
        this(cameraId, plate, speed, limit, detectedAt, null);
    }

    public Detection(String cameraId, String plate, int speed, int limit, Instant detectedAt, Location location) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing speed camera");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("The detected speed must be positive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("The speed limit must be positive");
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
    public Optional<Location> getLocation() { return Optional.ofNullable(location); }

    /**
     * The fine applies only if the limit is exceeded even after removing the measurement error margin:
     * below 100 km/h speed - 5 &gt; limit; from 100 km/h up speed * 95% &gt; limit.
     * Example with limit 50: 55 km/h is not a violation, 56 is.
     * The percentage is compared without divisions (speed * 95 &gt; limit * 100) to avoid truncating decimals.
     */
    public boolean isViolation() {
        if (speed < PERCENTAGE_THRESHOLD_KMH) {
            return speed - TOLERANCE_KMH > limit;
        }
        return (long) speed * 95 > (long) limit * 100;
    }

    /**
     * Km/h over the limit computed on the DETECTED speed (0 if within the limit), without the measurement
     * margin: it can be positive even when isViolation() is false. Only used for the amount.
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
        return "Detection[" + cameraId + " " + plate + " " + speed + "/" + limit + " km/h @ " + detectedAt + "]";
    }
}
