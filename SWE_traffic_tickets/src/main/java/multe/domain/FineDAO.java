package fines.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Accesso ai dati delle multe: nasconde il database al resto del sistema. */
public interface FineDAO {
    void save(Fine fine);
    void update(Fine fine);
    Optional<Fine> findById(UUID id);
    List<Fine> findByOwner(int userId);
}
