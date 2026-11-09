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
 * Conta i tentativi falliti per ACCOUNT e dice quando avvisare il titolare. NON blocca mai nessuno:
 * bloccare un account permetterebbe a chi conosce un codice fiscale (che non è segreto) di tenerne fuori il titolare.
 *
 * Regole: raggiunta la {@code threshold} di errori dentro la {@code window} (che parte dal primo errore) si avvisa
 * UNA volta sola; poi si tace fino a quando la finestra scade o il titolare entra con successo. Così l'attaccante
 * non può usare gli avvisi per inondare di email il titolare, e un accesso riuscito (che solo il titolare
 * può fare) riarma l'avviso.
 * La chiave la sceglie in parte l'attaccante, quindi il numero di chiavi ha un tetto.
 */
public final class AccountMonitor {

    private static final int DEFAULT_MAX_KEYS = 100_000;

    private static final class MonitorState {
        final Instant firstFailure;
        final int errors;
        final boolean alerted;

        MonitorState(Instant firstFailure, int errors, boolean alerted) {
            this.firstFailure = firstFailure;
            this.errors = errors;
            this.alerted = alerted;
        }
    }

    private final Map<String, MonitorState> states = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int threshold;
    private final Duration window;
    private final int maxKeys;

    public AccountMonitor(Clock clock) {
        this(clock, 10, Duration.ofHours(24), DEFAULT_MAX_KEYS);
    }

    public AccountMonitor(Clock clock, int threshold, Duration window, int maxKeys) {
        if (threshold <= 0 || maxKeys <= 0) {
            throw new IllegalArgumentException("Soglia e numero massimo di chiavi devono essere positivi");
        }
        this.clock = Objects.requireNonNull(clock);
        this.threshold = threshold;
        this.window = Objects.requireNonNull(window);
        this.maxKeys = maxKeys;
    }

    /**
     * @return il numero di errori se con questo il titolare va avvisato (soglia raggiunta per la prima volta
     *         nella finestra), altrimenti 0
     */
    public int registerFailure(String key) {
        if (key == null) {
            return 0;
        }
        Instant now = clock.instant();
        int[] toAlert = {0};
        states.compute(key, (k, old) -> {
            MonitorState s = (old == null || expired(old, now)) ? new MonitorState(now, 0, false) : old;
            int errors = s.errors + 1;
            boolean alerted = s.alerted;
            if (!alerted && errors >= threshold) {
                alerted = true;
                toAlert[0] = errors;
            }
            return new MonitorState(s.firstFailure, errors, alerted);
        });
        cleanup(now);
        return toAlert[0];
    }

    /** Un accesso riuscito azzera il conteggio e riarma l'avviso. */
    public void registerSuccess(String key) {
        if (key != null) {
            states.remove(key);
        }
    }

    /** Quante chiavi sono tracciate adesso (mai più di circa {@code maxKeys}). */
    public int trackedKeys() {
        return states.size();
    }

    private void cleanup(Instant now) {
        if (states.size() <= maxKeys) {
            return;
        }
        states.values().removeIf(s -> expired(s, now));
        if (states.size() <= maxKeys) {
            return;
        }
        List<Map.Entry<String, MonitorState>> entries = new ArrayList<>(states.entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().firstFailure));
        int toRemove = states.size() - maxKeys * 9 / 10;
        for (int i = 0; i < entries.size() && i < toRemove; i++) {
            states.remove(entries.get(i).getKey(), entries.get(i).getValue());
        }
    }

    private boolean expired(MonitorState s, Instant now) {
        return !now.isBefore(s.firstFailure.plus(window));
    }
}
