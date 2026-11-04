package fines.persistence;

import fines.domain.Fine;
import fines.domain.FineDAO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione in memoria, utile per sviluppare e testare prima di avere il database.
 * Tiene copie delle multe: quello che esce dal DAO non è mai lo stesso oggetto di dominio.
 * La mappa è concurrent perché più multe possono cambiare stato in parallelo.
 */
public class InMemoryFineDAO implements FineDAO {

    private final Map<UUID, Fine> rows = new ConcurrentHashMap<>();

    @Override
    public void save(Fine fine) {
        if (rows.putIfAbsent(fine.getId(), fine.copy()) != null) {
            throw new IllegalStateException("Multa già presente: " + fine.getId());
        }
    }

    @Override
    public void update(Fine fine) {
        if (rows.replace(fine.getId(), fine.copy()) == null) {
            throw new IllegalStateException("Multa inesistente: " + fine.getId());
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
        result.sort(Comparator.comparing(Fine::getId));
        return result;
    }
}
