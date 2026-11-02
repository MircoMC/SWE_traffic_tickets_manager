package fines.domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Identity Map: una sola istanza di Multa per id. Visibile solo dentro il package:
 * gli oggetti originali non escono mai, all'esterno si ottengono solo copie
 * (tramite {@link FineService#getCopy(int)}).
 * Mappa concurrent perché StateManager e FineMaker lavorano in parallelo.
 */
class IdentityMap {

    private final Map<Integer, Fine> fines = new ConcurrentHashMap<>();

    void register(Fine m) {
        Fine existing = fines.putIfAbsent(m.getId(), m);
        if (existing != null && existing != m) {
            throw new IllegalStateException("Esiste già una multa con id " + m.getId());
        }
    }

    /** L'oggetto originale (null se assente). Solo per StateManager e FineMaker. */
    Fine get(int id) {
        return fines.get(id);
    }

    /** Copia indipendente della multa (null se assente): l'unica cosa che può uscire dal package. */
    Fine getCopy(int id) {
        Fine original = fines.get(id);
        return original == null ? null : original.copy();
    }
}
