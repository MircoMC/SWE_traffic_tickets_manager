package multe.persistenza;

import multe.dominio.Multa;
import multe.dominio.MultaDAO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione in memoria, utile per sviluppare e testare prima di avere il database.
 * Tiene copie delle multe: quello che esce dal DAO non è mai lo stesso oggetto di dominio.
 * La mappa è concurrent perché più multe possono cambiare stato in parallelo.
 */
public class MultaDAOInMemory implements MultaDAO {

    private final Map<Integer, Multa> righe = new ConcurrentHashMap<>();

    @Override
    public void salva(Multa multa) {
        if (righe.putIfAbsent(multa.getId(), multa.copia()) != null) {
            throw new IllegalStateException("Multa già presente: " + multa.getId());
        }
    }

    @Override
    public void aggiorna(Multa multa) {
        if (righe.replace(multa.getId(), multa.copia()) == null) {
            throw new IllegalStateException("Multa inesistente: " + multa.getId());
        }
    }

    @Override
    public Optional<Multa> trovaPerId(int id) {
        return Optional.ofNullable(righe.get(id)).map(Multa::copia);
    }

    @Override
    public List<Multa> trovaPerIntestatario(int idUtente) {
        List<Multa> risultato = new ArrayList<>();
        for (Multa m : righe.values()) {
            if (m.getIdIntestatario() == idUtente) {
                risultato.add(m.copia());
            }
        }
        risultato.sort(Comparator.comparingInt(Multa::getId));
        return risultato;
    }
}
