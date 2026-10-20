import java.util.HashMap;
import java.util.Map;

/** Identity Map: una sola istanza di Multa per id. */
public class MapperId {

    private final Map<Integer, Multa> multe = new HashMap<>();

    public void register(Multa m) {
        Multa esistente = multe.putIfAbsent(m.getId(), m);
        if (esistente != null && esistente != m) {
            throw new IllegalStateException("Esiste già una multa con id " + m.getId());
        }
    }

    public Multa get(int id) {
        return multe.get(id);
    }
}
