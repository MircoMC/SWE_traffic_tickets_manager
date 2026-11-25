package fines.plates;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Associates every plate with its owner, KEEPING THE HISTORY: needed to know who the owner was
 * at the instant of a violation even if the detection arrives after a sale.
 * A user can have several plates.
 */
public interface PlateRegistry {

    /** Assigns the plate to the user from now on: if it already had an owner it is an ownership transfer. */
    void assign(String plate, int ownerId);

    /** Assigns the plate to the user from the given instant (even in the past, to insert the history). */
    void assign(String plate, int ownerId, Instant since);

    /** Owner at the given instant; empty if the plate was unknown at that moment. */
    Optional<Integer> ownerAt(String plate, Instant instant);

    Optional<Integer> currentOwner(String plate);

    /** Plates that currently belong to the user. */
    Set<String> platesOf(int ownerId);

    /** The same plate written in different ways ("ab123cd ", "AB123CD") = same key. */
    static String normalize(String plate) {
        if (plate == null || plate.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing plate");
        }
        return plate.trim().toUpperCase(Locale.ROOT);
    }
}
