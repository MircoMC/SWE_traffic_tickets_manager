package fines.login;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocco progressivo dei tentativi di accesso falliti, per CHIAVE (un dispositivo, un account...).
 *
 * Regole: dopo {@code threshold} errori la chiave è bloccata per {@code initialBlock}; ogni errore successivo
 * raddoppia il blocco (mai oltre la finestra). La finestra parte dal PRIMO errore e dopo {@code window}
 * (di default 24 ore) il conteggio si azzera. Un accesso riuscito azzera la chiave (se il chiamante lo chiede).
 *
 * La chiave deve essere un dato che decide il SERVER (l'IP, un identificativo del dispositivo emesso dal server
 * dopo un primo accesso), non uno che dichiara il client: il MAC address, per esempio, non arriva a un server
 * remoto e chi lo scrive può cambiarlo a ogni tentativo.
 * Una chiave null non viene mai bloccata (serve a chi non ha un dispositivo da indicare).
 *
 * CONCORRENZA: per chi tenta gli accessi usa {@link #tryAttempt} e {@link #release}, che controllano il blocco
 * e contano il tentativo in UN SOLO passo. Controllare il blocco e registrare l'errore dopo, in due passi, lascerebbe
 * passare tutti i tentativi lanciati insieme prima che il primo errore sia contato.
 *
 * MEMORIA: le chiavi arrivano in parte dall'esterno, quindi il loro numero ha un tetto ({@code maxKeys}):
 * oltre, si tolgono prima le voci scadute e poi le più vecchie tra quelle NON bloccate (una chiave bloccata
 * non si libera sommergendo il limitatore di chiavi nuove).
 */
public final class AttemptLimiter {

    private static final int DEFAULT_MAX_KEYS = 100_000;

    private static final class AttemptState {
        final Instant firstFailure;
        final int errors;
        final Instant blockedUntil;   // null se non bloccato

        AttemptState(Instant firstFailure, int errors, Instant blockedUntil) {
            this.firstFailure = firstFailure;
            this.errors = errors;
            this.blockedUntil = blockedUntil;
        }
    }

    private final Map<String, AttemptState> states = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int threshold;
    private final Duration initialBlock;
    private final Duration window;
    private final int maxKeys;

    public AttemptLimiter(Clock clock) {
        this(clock, 3, Duration.ofMinutes(1), Duration.ofHours(24));
    }

    public AttemptLimiter(Clock clock, int threshold, Duration initialBlock, Duration window) {
        this(clock, threshold, initialBlock, window, DEFAULT_MAX_KEYS);
    }

    public AttemptLimiter(Clock clock, int threshold, Duration initialBlock, Duration window, int maxKeys) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("La soglia deve essere positiva");
        }
        if (maxKeys <= 0) {
            throw new IllegalArgumentException("Il numero massimo di chiavi deve essere positivo");
        }
        this.clock = Objects.requireNonNull(clock);
        this.threshold = threshold;
        this.initialBlock = Objects.requireNonNull(initialBlock);
        this.window = Objects.requireNonNull(window);
        this.maxKeys = maxKeys;
    }

    /** @return true se in questo momento la chiave non può tentare l'accesso */
    public boolean isBlocked(String key) {
        if (key == null) {
            return false;
        }
        AttemptState s = states.get(key);
        if (s == null) {
            return false;
        }
        Instant now = clock.instant();
        if (expired(s, now)) {
            states.remove(key, s);
            return false;
        }
        return blocked(s, now);
    }

    /**
     * Prenota un tentativo di accesso: controlla il blocco E conta il tentativo nello stesso passo atomico,
     * come se dovesse fallire. Se poi l'accesso riesce, il chiamante lo restituisce con {@link #release}.
     *
     * @return false se la chiave è bloccata (il tentativo non va valutato e non viene contato), true altrimenti
     */
    public boolean tryAttempt(String key) {
        if (key == null) {
            return true;
        }
        Instant now = clock.instant();
        boolean[] allowed = {true};
        states.compute(key, (k, old) -> {
            if (old != null && !expired(old, now) && blocked(old, now)) {
                allowed[0] = false;
                return old;
            }
            return withOneMoreError(old, now);
        });
        cleanup(now);
        return allowed[0];
    }

    /**
     * Restituisce un tentativo prenotato con {@link #tryAttempt} (l'accesso è riuscito): non conta come errore
     * e non azzera nemmeno gli altri, così un accesso riuscito non "ricarica" i tentativi della chiave.
     */
    public void release(String key) {
        if (key == null) {
            return;
        }
        states.computeIfPresent(key, (k, s) -> {
            int errors = s.errors - 1;
            if (errors <= 0) {
                return null;
            }
            return new AttemptState(s.firstFailure, errors, errors >= threshold ? s.blockedUntil : null);
        });
    }

    public void registerFailure(String key) {
        if (key == null) {
            return;
        }
        Instant now = clock.instant();
        states.compute(key, (k, old) -> withOneMoreError(old, now));
        cleanup(now);
    }

    /** Azzera la chiave (dopo un accesso riuscito). */
    public void registerSuccess(String key) {
        if (key != null) {
            states.remove(key);
        }
    }

    /** Quante chiavi sono tracciate adesso (mai più di circa {@code maxKeys} se non sono tutte bloccate). */
    public int trackedKeys() {
        return states.size();
    }

    private AttemptState withOneMoreError(AttemptState old, Instant now) {
        AttemptState s = (old == null || expired(old, now)) ? new AttemptState(now, 0, null) : old;
        int errors = s.errors + 1;
        Instant until = null;
        if (errors >= threshold) {
            long doublings = Math.min(errors - threshold, 30);
            Duration block = initialBlock.multipliedBy(1L << doublings);
            if (block.compareTo(window) > 0) {
                block = window;
            }
            until = now.plus(block);
        }
        return new AttemptState(s.firstFailure, errors, until);
    }

    private void cleanup(Instant now) {
        if (states.size() <= maxKeys) {
            return;
        }
        states.values().removeIf(s -> expired(s, now));
        if (states.size() <= maxKeys) {
            return;
        }
        List<Map.Entry<String, AttemptState>> candidates = new ArrayList<>();
        for (Map.Entry<String, AttemptState> e : states.entrySet()) {
            if (!blocked(e.getValue(), now)) {
                candidates.add(e);
            }
        }
        candidates.sort(Comparator.comparing(e -> e.getValue().firstFailure));
        int toRemove = states.size() - maxKeys * 9 / 10;
        for (int i = 0; i < candidates.size() && i < toRemove; i++) {
            states.remove(candidates.get(i).getKey(), candidates.get(i).getValue());
        }
    }

    private boolean blocked(AttemptState s, Instant now) {
        return s.blockedUntil != null && now.isBefore(s.blockedUntil);
    }

    private boolean expired(AttemptState s, Instant now) {
        return !now.isBefore(s.firstFailure.plus(window));
    }
}
