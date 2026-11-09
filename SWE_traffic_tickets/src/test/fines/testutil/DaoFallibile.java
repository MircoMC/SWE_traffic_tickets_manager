package fines.testutil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import fines.domain.Fine;
import fines.domain.FineDAO;
import fines.persistence.InMemoryFineDAO;

/** DAO in memoria che può essere fatto fallire in update(), per simulare un database non raggiungibile. */
public final class DaoFallibile implements FineDAO {

    private final InMemoryFineDAO base = new InMemoryFineDAO();
    private volatile boolean fallisciAggiorna;

    public void fallisciAggiorna(boolean f) { this.fallisciAggiorna = f; }

    @Override public void save(Fine m) { base.save(m); }

    @Override
    public void update(Fine m) {
        if (fallisciAggiorna) {
            throw new IllegalStateException("Database non raggiungibile");
        }
        base.update(m);
    }

    @Override public Optional<Fine> findById(UUID id)           { return base.findById(id); }
    @Override public List<Fine> findByOwner(int idUtente) { return base.findByOwner(idUtente); }
}
