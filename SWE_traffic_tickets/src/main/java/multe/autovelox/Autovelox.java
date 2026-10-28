package multe.autovelox;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;
import multe.dominio.Rilevazione;

/**
 * Un autovelox misura la velocità e, se c'è infrazione (vedi Rilevazione.isInfrazione), consegna una Rilevazione
 * al dominio (in pratica {@code servizi::creaMulta}). Non conosce multe, stati né proprietari.
 * Il limite in vigore viene fotografato dentro ogni Rilevazione: cambiarlo poi non tocca le multe già emesse.
 */
public class Autovelox {

    private final String id;
    private final Location location;
    private final ToIntFunction<Rilevazione> destinatario;
    private final Clock clock;
    private volatile int limite;     // km/h

    public Autovelox(String id, int limite, Location location,
                     ToIntFunction<Rilevazione> destinatario, Clock clock) {
        this.id = Objects.requireNonNull(id);
        this.location = Objects.requireNonNull(location);
        this.destinatario = Objects.requireNonNull(destinatario);
        this.clock = Objects.requireNonNull(clock);
        validaLimite(limite);
        this.limite = limite;
    }

    /**
     * Misura un veicolo all'istante del Clock.
     *
     * @return l'id della multa creata, vuoto se non è infrazione (margine di misura di legge incluso)
     */
    public OptionalInt rileva(String targa, int velocita) {
        Instant adesso = clock.instant();
        Rilevazione rilevazione = new Rilevazione(id, targa, velocita, limite, adesso);
        if (!rilevazione.isInfrazione()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(destinatario.applyAsInt(rilevazione));
    }

    /**
     * TODO permessi: dovrebbe poterlo fare solo un amministratore, ma oggi non c'è nessun controllo
     * (chi ha il riferimento all'Autovelox può cambiarlo). Da proteggere con una capability data dal login,
     * come per l'Annullamento, prima di esporre gli autovelox a dei client.
     */
    public void setLimite(int limite) {
        validaLimite(limite);
        this.limite = limite;
    }

    private static void validaLimite(int limite) {
        if (limite <= 0) {
            throw new IllegalArgumentException("Il limite di velocità deve essere positivo");
        }
    }

    public String getId()         { return id; }
    public int getLimite()        { return limite; }
    public Location getLocation() { return location; }
}
