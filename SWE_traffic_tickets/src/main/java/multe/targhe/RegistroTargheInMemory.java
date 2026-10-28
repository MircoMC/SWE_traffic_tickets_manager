package multe.targhe;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/** Versione in memoria; in seguito si può sostituire con una tabella del database (targa, proprietario, dal). */
public class RegistroTargheInMemory implements RegistroTarghe {

    /** Per ogni targa: dall'istante X il proprietario è Y. */
    private final Map<String, NavigableMap<Instant, Integer>> storia = new ConcurrentHashMap<>();
    private final Clock clock;

    public RegistroTargheInMemory() {
        this(Clock.systemUTC());
    }

    public RegistroTargheInMemory(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void assegna(String targa, int idProprietario) {
        assegna(targa, idProprietario, clock.instant());
    }

    @Override
    public void assegna(String targa, int idProprietario, Instant dal) {
        Objects.requireNonNull(dal);
        storia.computeIfAbsent(RegistroTarghe.normalizza(targa), k -> new ConcurrentSkipListMap<>())
              .put(dal, idProprietario);
    }

    @Override
    public Optional<Integer> proprietarioAl(String targa, Instant istante) {
        NavigableMap<Instant, Integer> periodi = storia.get(RegistroTarghe.normalizza(targa));
        if (periodi == null) {
            return Optional.empty();
        }
        Map.Entry<Instant, Integer> valido = periodi.floorEntry(istante);
        return valido == null ? Optional.empty() : Optional.of(valido.getValue());
    }

    @Override
    public Optional<Integer> proprietarioAttuale(String targa) {
        return proprietarioAl(targa, clock.instant());
    }

    @Override
    public Set<String> targheDi(int idProprietario) {
        Instant adesso = clock.instant();
        Set<String> risultato = new TreeSet<>();
        for (Map.Entry<String, NavigableMap<Instant, Integer>> e : storia.entrySet()) {
            Map.Entry<Instant, Integer> valido = e.getValue().floorEntry(adesso);
            if (valido != null && valido.getValue() == idProprietario) {
                risultato.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(risultato);
    }
}
