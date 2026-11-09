package fines.plates;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Associa ogni targa al suo proprietario, TENENDO LA STORIA: serve a sapere chi era il proprietario
 * all'istante di un'infrazione anche se la rilevazione arriva dopo una vendita.
 * Un utente può avere più targhe.
 */
public interface PlateRegistry {

    /** Assegna la targa all'utente da adesso: se aveva già un proprietario è un passaggio di proprietà. */
    void assign(String plate, int ownerId);

    /** Assegna la targa all'utente a partire dall'istante indicato (anche nel passato, per inserire la storia). */
    void assign(String plate, int ownerId, Instant from);

    /** Proprietario all'istante dato; vuoto se la targa era sconosciuta in quel momento. */
    Optional<Integer> ownerAt(String plate, Instant instant);

    Optional<Integer> currentOwner(String plate);

    /** Targhe che adesso appartengono all'utente. */
    Set<String> platesOf(int ownerId);

    /** Stessa targa scritta in modi diversi ("ab123cd ", "AB123CD") = stessa chiave. */
    static String normalize(String plate) {
        if (plate == null || plate.trim().isEmpty()) {
            throw new IllegalArgumentException("Targa mancante");
        }
        return plate.trim().toUpperCase(Locale.ROOT);
    }
}
