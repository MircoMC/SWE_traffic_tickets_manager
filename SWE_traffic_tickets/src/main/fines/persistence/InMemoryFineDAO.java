package fines.persistence;

import fines.domain.Fine;
import fines.domain.FineDAO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation, useful to develop and test before having the database.
 * It keeps copies of the fines: what comes out of the DAO is never the same domain object.
 * The map is concurrent because several fines can change state in parallel.
 */
public class InMemoryFineDAO implements FineDAO {

    private final Map<UUID, Fine> rows = new ConcurrentHashMap<>();

    @Override
    public void save(Fine fine) {
        if (rows.putIfAbsent(fine.getId(), fine.copy()) != null) {
            throw new IllegalStateException("Fine already present: " + fine.getId());
        }
    }

    @Override
    public void update(Fine fine) {
        if (rows.replace(fine.getId(), fine.copy()) == null) {
            throw new IllegalStateException("Fine not found: " + fine.getId());
        }
    }

    @Override
    public Optional<Fine> findById(UUID id) {
        return Optional.ofNullable(rows.get(id)).map(Fine::copy);
    }

    @Override
    public List<Fine> findByOwner(int userId) {
        List<Fine> result = new ArrayList<>();
        for (Fine m : rows.values()) {
            if (m.getOwnerId() == userId) {
                result.add(m.copy());
            }
        }
        result.sort(Comparator.comparing((Fine f) -> f.getDetection().getDetectedAt()).thenComparing(Fine::getId));
        return result;
    }
}
