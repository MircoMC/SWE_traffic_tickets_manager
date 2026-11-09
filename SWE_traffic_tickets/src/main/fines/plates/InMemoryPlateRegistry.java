package fines.plates;

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
public class InMemoryPlateRegistry implements PlateRegistry {

    /** Per ogni targa: dall'istante X il proprietario è Y. */
    private final Map<String, NavigableMap<Instant, Integer>> history = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryPlateRegistry() {
        this(Clock.systemUTC());
    }

    public InMemoryPlateRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void assign(String plate, int ownerId) {
        assign(plate, ownerId, clock.instant());
    }

    @Override
    public void assign(String plate, int ownerId, Instant from) {
        Objects.requireNonNull(from);
        history.computeIfAbsent(PlateRegistry.normalize(plate), k -> new ConcurrentSkipListMap<>())
              .put(from, ownerId);
    }

    @Override
    public Optional<Integer> ownerAt(String plate, Instant instant) {
        NavigableMap<Instant, Integer> periods = history.get(PlateRegistry.normalize(plate));
        if (periods == null) {
            return Optional.empty();
        }
        Map.Entry<Instant, Integer> valid = periods.floorEntry(instant);
        return valid == null ? Optional.empty() : Optional.of(valid.getValue());
    }

    @Override
    public Optional<Integer> currentOwner(String plate) {
        return ownerAt(plate, clock.instant());
    }

    @Override
    public Set<String> platesOf(int ownerId) {
        Instant now = clock.instant();
        Set<String> result = new TreeSet<>();
        for (Map.Entry<String, NavigableMap<Instant, Integer>> e : history.entrySet()) {
            Map.Entry<Instant, Integer> valid = e.getValue().floorEntry(now);
            if (valid != null && valid.getValue() == ownerId) {
                result.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
