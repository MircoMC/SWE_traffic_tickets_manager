package fines.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to the fines data: hides the database from the rest of the system.
 *
 * CONTRACT: findById and findByOwner return NEW, independent objects at every call
 * (the domain keeps them as the single original in the IdentityMap).
 */
public interface FineDAO {
    void save(Fine fine);
    void update(Fine fine);
    Optional<Fine> findById(UUID id);
    List<Fine> findByOwner(int userId);
}
