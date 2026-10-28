package multe.autovelox;

import java.util.Objects;

/** Value object: due posizioni con gli stessi dati sono uguali. */
public final class Location {

    private final String indirizzo;
    private final double latitude;
    private final double longitude;

    public Location(String indirizzo, double latitude, double longitude) {
        this.indirizzo = Objects.requireNonNull(indirizzo);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getIndirizzo() { return indirizzo; }
    public double getLatitude()  { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location l = (Location) o;
        return Double.compare(latitude, l.latitude) == 0
            && Double.compare(longitude, l.longitude) == 0
            && indirizzo.equals(l.indirizzo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indirizzo, latitude, longitude);
    }

    @Override
    public String toString() {
        return indirizzo + " (" + latitude + ", " + longitude + ")";
    }
}
