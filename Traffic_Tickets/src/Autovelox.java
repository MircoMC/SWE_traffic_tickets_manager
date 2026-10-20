import java.util.Objects;

public class Autovelox {

    private final String id;
    private final Location location;
    private double limite;

    public Autovelox(String id, double limite, Location location) {
        this.id = Objects.requireNonNull(id);
        this.location = Objects.requireNonNull(location);
        validaLimite(limite);
        this.limite = limite;
    }

    /** Riservata all'amministratore: il controllo dei permessi sta nel proxy. */
    public void setLimite(double limite) {
        validaLimite(limite);
        this.limite = limite;
    }

    private static void validaLimite(double limite) {
        if (limite <= 0) {
            throw new IllegalArgumentException("Il limite di velocità deve essere positivo");
        }
    }

    public String getId()         { return id; }
    public double getLimite()     { return limite; }
    public Location getLocation() { return location; }
}
