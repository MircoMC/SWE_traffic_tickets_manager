package fines.domain;

import java.util.List;
import java.util.Optional;

/** Accesso ai dati delle multe: nasconde il database al resto del sistema. */
public interface FineDAO {
    void save(Fine fine);
    void update(Fine fine);
    Optional<Fine> findById(int id);
    List<Fine> findByOwner(int userId);
}
