package multe.dominio;

import java.util.List;
import java.util.Optional;

/** Accesso ai dati delle multe: nasconde il database al resto del sistema. */
public interface MultaDAO {
    void salva(Multa multa);
    void aggiorna(Multa multa);
    Optional<Multa> trovaPerId(int id);
    List<Multa> trovaPerIntestatario(int idUtente);
}
