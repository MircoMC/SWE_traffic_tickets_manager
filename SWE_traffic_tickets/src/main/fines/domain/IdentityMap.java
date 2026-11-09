package fines.domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Identity Map: una sola istanza di Fine per id. Visibile solo dentro il package:
 * gli oggetti originali non escono mai, all'esterno si ottengono solo copie
 * (tramite {@link FineService#getCopy(java.util.UUID)}).
 * Mappa concurrent perché StateManager e FineMaker lavorano in parallelo.
 */
class IdentityMap {

    private final Map<UUID, Fine> fines = new ConcurrentHashMap<>();

    void register(Fine m) {
        Fine existing = fines.putIfAbsent(m.getId(), m);
        if (existing != null && existing != m) {
            throw new IllegalStateException("Esiste già una multa con id " + m.getId());
        }
    }

    /** L'oggetto originale (null se assente). Solo per StateManager e FineMaker. */
    Fine get(UUID id) {
        return fines.get(id);
    }

    /** Copia coerente e indipendente della multa (null se assente): l'unica cosa che può uscire dal package. */
    Fine getCopy(UUID id) {
        Fine original = fines.get(id);
        if (original == null) {
            return null;
        }
        // sotto il lock della multa: stato e provvedimento vengono letti insieme, mai a metà di una transizione
        synchronized (original.lock) {
            return original.copy();
        }
    }
}
