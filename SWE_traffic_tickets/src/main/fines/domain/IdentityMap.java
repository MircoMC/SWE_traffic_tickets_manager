package fines.domain;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Identity Map: one Fine instance per id, so every operation on the same fine shares the same state and lock.
 * It is a CACHE over the DAO (the single source of truth): a fine that is not in memory, for instance after
 * a restart, is loaded from the DAO and gets the registered observers attached.
 * The original objects never leave the package: from outside only copies are available.
 */
class IdentityMap {

    private final Map<UUID, Fine> fines = new ConcurrentHashMap<>();
    private final FineDAO dao;
    private final Consumer<Fine> attachObservers;

    /** Without DAO: a pure in-memory identity map (used by tests of the domain alone). */
    IdentityMap() {
        this(null, f -> { });
    }

    IdentityMap(FineDAO dao, Consumer<Fine> attachObservers) {
        this.dao = dao;
        this.attachObservers = attachObservers;
    }

    void register(Fine fine) {
        Fine existing = fines.putIfAbsent(fine.getId(), fine);
        if (existing != null && existing != fine) {
            throw new IllegalStateException("A fine with id " + fine.getId() + " already exists");
        }
    }

    /** The original object (null if the fine does not exist). Only for StateManager and FineMaker. */
    Fine get(UUID id) {
        Fine cached = fines.get(id);
        if (cached != null || dao == null) {
            return cached;
        }
        return fines.computeIfAbsent(id, k -> dao.findById(k).map(f -> {
            attachObservers.accept(f);
            return f;
        }).orElse(null));
    }

    /** Independent copy (null if absent): the only thing that can leave the package. */
    Fine getCopy(UUID id) {
        Fine original = get(id);
        if (original == null) {
            return null;
        }
        synchronized (original.lock) {      // never read the state in the middle of a transition
            return original.copy();
        }
    }
}
