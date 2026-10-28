package multe.dominio;

import java.time.Instant;
import java.util.Objects;
import multe.targhe.RegistroTarghe;

/**
 * Value object: una misura fatta da un autovelox. Porta con sé tutto ciò che serve a decidere la multa:
 * il limite IN VIGORE al momento della misura (se poi l'autovelox viene riconfigurato la multa non cambia)
 * e l'istante, che serve a trovare chi era il proprietario della targa in quel momento.
 */
public final class Rilevazione {

    /** Margine per gli errori di misura: sotto i 100 km/h si tolgono 5 km/h dalla velocità rilevata... */
    static final int TOLLERANZA_KMH = 5;
    /** ...da 100 km/h in su si toglie il 5% (a 100 km/h i due margini coincidono: 5 km/h). */
    static final int SOGLIA_PERCENTUALE_KMH = 100;

    private final String idAutovelox;
    private final String targa;          // già normalizzata
    private final int velocita;          // km/h
    private final int limite;            // km/h
    private final Instant rilevataIl;

    public Rilevazione(String idAutovelox, String targa, int velocita, int limite, Instant rilevataIl) {
        if (idAutovelox == null || idAutovelox.trim().isEmpty()) {
            throw new IllegalArgumentException("Autovelox mancante");
        }
        if (velocita <= 0) {
            throw new IllegalArgumentException("La velocità rilevata deve essere positiva");
        }
        if (limite <= 0) {
            throw new IllegalArgumentException("Il limite di velocità deve essere positivo");
        }
        this.idAutovelox = idAutovelox;
        this.targa = RegistroTarghe.normalizza(targa);
        this.velocita = velocita;
        this.limite = limite;
        this.rilevataIl = Objects.requireNonNull(rilevataIl);
    }

    public String getIdAutovelox() { return idAutovelox; }
    public String getTarga()       { return targa; }
    public int getVelocita()       { return velocita; }
    public int getLimite()         { return limite; }
    public Instant getRilevataIl() { return rilevataIl; }

    /**
     * La multa scatta solo se il limite è superato anche togliendo il margine di errore di misura:
     * sotto i 100 km/h velocità - 5 &gt; limite; da 100 km/h in su velocità * 95% &gt; limite.
     * Esempio con limite 50: 55 km/h non è infrazione, 56 sì.
     * La percentuale si confronta senza divisioni (velocità * 95 &gt; limite * 100) per non troncare i decimali.
     */
    public boolean isInfrazione() {
        if (velocita < SOGLIA_PERCENTUALE_KMH) {
            return velocita - TOLLERANZA_KMH > limite;
        }
        return (long) velocita * 95 > (long) limite * 100;
    }

    /**
     * Km/h oltre il limite calcolati sulla velocità RILEVATA (0 se entro il limite), senza il margine di
     * misura: può essere positivo anche quando isInfrazione() è false. Serve solo per l'importo.
     */
    public int getEccesso() { return Math.max(0, velocita - limite); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rilevazione)) return false;
        Rilevazione r = (Rilevazione) o;
        return velocita == r.velocita && limite == r.limite && idAutovelox.equals(r.idAutovelox)
            && targa.equals(r.targa) && rilevataIl.equals(r.rilevataIl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idAutovelox, targa, velocita, limite, rilevataIl);
    }

    @Override
    public String toString() {
        return "Rilevazione[" + idAutovelox + " " + targa + " " + velocita + "/" + limite + " km/h @ " + rilevataIl + "]";
    }
}
