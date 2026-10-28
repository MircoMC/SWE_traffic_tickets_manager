package multe.testutil;

import java.util.List;
import java.util.Optional;
import multe.dominio.Multa;
import multe.dominio.MultaDAO;
import multe.persistenza.MultaDAOInMemory;

/** DAO in memoria che può essere fatto fallire in aggiorna(), per simulare un database non raggiungibile. */
public final class DaoFallibile implements MultaDAO {

    private final MultaDAOInMemory base = new MultaDAOInMemory();
    private volatile boolean fallisciAggiorna;

    public void fallisciAggiorna(boolean f) { this.fallisciAggiorna = f; }

    @Override public void salva(Multa m) { base.salva(m); }

    @Override
    public void aggiorna(Multa m) {
        if (fallisciAggiorna) {
            throw new IllegalStateException("Database non raggiungibile");
        }
        base.aggiorna(m);
    }

    @Override public Optional<Multa> trovaPerId(int id)           { return base.trovaPerId(id); }
    @Override public List<Multa> trovaPerIntestatario(int idUtente) { return base.trovaPerIntestatario(idUtente); }
}
