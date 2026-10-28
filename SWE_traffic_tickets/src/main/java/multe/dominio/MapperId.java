package multe.dominio;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Identity Map: una sola istanza di Multa per id. Visibile solo dentro il package:
 * gli oggetti originali non escono mai, all'esterno si ottengono solo copie
 * (tramite {@link ServiziMulte#getCopia(int)}).
 * Mappa concurrent perché StateManager e MulteMaker lavorano in parallelo.
 */
class MapperId {

    private final Map<Integer, Multa> multe = new ConcurrentHashMap<>();

    void register(Multa m) {
        Multa esistente = multe.putIfAbsent(m.getId(), m);
        if (esistente != null && esistente != m) {
            throw new IllegalStateException("Esiste già una multa con id " + m.getId());
        }
    }

    /** L'oggetto originale (null se assente). Solo per StateManager e MulteMaker. */
    Multa get(int id) {
        return multe.get(id);
    }

    /** Copia indipendente della multa (null se assente): l'unica cosa che può uscire dal package. */
    Multa getCopia(int id) {
        Multa originale = multe.get(id);
        return originale == null ? null : originale.copia();
    }
}
