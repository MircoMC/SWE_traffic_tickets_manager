package multe.targhe;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Associa ogni targa al suo proprietario, TENENDO LA STORIA: serve a sapere chi era il proprietario
 * all'istante di un'infrazione anche se la rilevazione arriva dopo una vendita.
 * Un utente può avere più targhe.
 */
public interface RegistroTarghe {

    /** Assegna la targa all'utente da adesso: se aveva già un proprietario è un passaggio di proprietà. */
    void assegna(String targa, int idProprietario);

    /** Assegna la targa all'utente a partire dall'istante indicato (anche nel passato, per inserire la storia). */
    void assegna(String targa, int idProprietario, Instant dal);

    /** Proprietario all'istante dato; vuoto se la targa era sconosciuta in quel momento. */
    Optional<Integer> proprietarioAl(String targa, Instant istante);

    Optional<Integer> proprietarioAttuale(String targa);

    /** Targhe che adesso appartengono all'utente. */
    Set<String> targheDi(int idProprietario);

    /** Stessa targa scritta in modi diversi ("ab123cd ", "AB123CD") = stessa chiave. */
    static String normalizza(String targa) {
        if (targa == null || targa.trim().isEmpty()) {
            throw new IllegalArgumentException("Targa mancante");
        }
        return targa.trim().toUpperCase(Locale.ROOT);
    }
}
