package multe.storico;

import multe.dominio.Multa;

import java.util.List;

/**
 * Lo storico delle multe di un utente, indicato per id (un int non può essere "truccato" dal chiamante).
 * Interfaccia funzionale: nei test basta una lambda.
 */
public interface StoricoMulte {
    List<Multa> getMulte(int idUtente);
}
