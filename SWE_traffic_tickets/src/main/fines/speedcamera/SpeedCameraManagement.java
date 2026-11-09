package fines.speedcamera;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import fines.speedcamera.Location;

/**
 * Capability dell'amministratore: l'unico modo di cambiare la configurazione degli autovelox.
 * Il costruttore è package-private: nasce solo da SpeedCameraServices.managementFor.
 *
 * Il passato è in SOLA LETTURA: si può programmare solo un cambio che parte da un istante successivo ad adesso,
 * quindi nessuna misura già avvenuta (né giudicata, né in attesa di esserlo) cambia mai configurazione.
 * Un cambio programmato non si sovrascrive. Dopo ogni scrittura la cache di quell'autovelox viene invalidata.
 */
public final class SpeedCameraManagement {

    private final CachedSpeedCameraMap map;
    private final Clock clock;

    SpeedCameraManagement(CachedSpeedCameraMap map, Clock clock) {
        this.map = map;
        this.clock = clock;
    }

    /** La storia delle configurazioni (sola lettura). */
    public List<Period> history(String cameraId) {
        return Collections.unmodifiableList(new ArrayList<>(map.history(cameraId)));
    }

    /**
     * Programma un cambio di limite e posizione che parte da {@code dal}, che deve essere nel FUTURO.
     * Il controllo c'è qui e anche nel mapper: se il servizio sottostante lo accettasse, qui non passerebbe.
     *
     * @throws IllegalArgumentException se {@code dal} non è nel futuro, il limite non è positivo o l'id è vuoto
     * @throws IllegalStateException    se esiste già un cambio con lo stesso inizio
     */
    public void schedule(String cameraId, Instant from, int limit, Location location) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            throw new IllegalArgumentException("Autovelox mancante");
        }
        Objects.requireNonNull(from);
        if (!from.isAfter(clock.instant())) {
            throw new IllegalArgumentException(
                "Il passato e il presente non si modificano: la configurazione deve partire da un istante futuro");
        }
        map.schedule(cameraId, from, new Configuration(limit, location));
    }

    /** Refresh manuale: serve per i cambi fatti fuori da questo sistema. */
    public void refresh() {
        map.refresh();
    }

    public void refresh(String cameraId) {
        map.refresh(cameraId);
    }
}
