package fines.speedcamera;

import java.util.Objects;
import fines.speedcamera.Location;

/** Dove sta un autovelox e quale limite deve far rispettare. Immutabile. */
public final class Configuration {

    private final int limit;          // km/h
    private final Location location;

    public Configuration(int limit, Location location) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Il limite di velocità deve essere positivo");
        }
        this.limit = limit;
        this.location = Objects.requireNonNull(location);
    }

    public int getLimit()         { return limit; }
    public Location getLocation() { return location; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Configuration)) return false;
        Configuration c = (Configuration) o;
        return limit == c.limit && location.equals(c.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, location);
    }
}
