package fines.speedcamera;

import java.time.Clock;
import java.util.Objects;
import fines.users.Administrator;

/**
 * Nucleo FIDATO per la gestione degli autovelox, come FineService per le multe: lo hanno solo il codice di
 * assemblaggio e il LoginProxy. I client ricevono dal login soltanto ciò che il loro ruolo consente, e la
 * SpeedCameraManagement la ottiene solo un amministratore che ha fatto l'accesso con la carica.
 */
public final class SpeedCameraServices {

    private final CachedSpeedCameraMap map;
    private final Clock clock;

    public SpeedCameraServices(CachedSpeedCameraMap map, Clock clock) {
        this.map = Objects.requireNonNull(map);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Il tipo del parametro lo rende possibile solo per un Administrator (che nasce dal login con la carica). */
    public SpeedCameraManagement managementFor(Administrator administrator) {
        Objects.requireNonNull(administrator);
        return new SpeedCameraManagement(map, clock);
    }
}
