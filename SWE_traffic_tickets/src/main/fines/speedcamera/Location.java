package fines.speedcamera;

import java.util.Objects;

/** Value object: due posizioni con gli stessi dati sono uguali. */
public final class Location {

    private final String address;
    private final double latitude;
    private final double longitude;

    public Location(String address, double latitude, double longitude) {
        this.address = Objects.requireNonNull(address);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAddress() { return address; }
    public double getLatitude()  { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location l = (Location) o;
        return Double.compare(latitude, l.latitude) == 0
            && Double.compare(longitude, l.longitude) == 0
            && address.equals(l.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, latitude, longitude);
    }

    @Override
    public String toString() {
        return address + " (" + latitude + ", " + longitude + ")";
    }
}
