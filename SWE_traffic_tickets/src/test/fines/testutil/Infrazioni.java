package fines.testutil;

import java.time.Clock;
import java.util.UUID;
import fines.domain.Detection;
import fines.domain.FineService;

/** Scorciatoie per i test: un'infrazione rilevata "adesso" da un autovelox con limite 50 km/h. */
public final class Infrazioni {

    public static final String AUTOVELOX = "A1";
    public static final int LIMITE = 50;

    private Infrazioni() {}

    public static Detection di(String targa, int velocita, Clock clock) {
        return new Detection(AUTOVELOX, targa, velocita, LIMITE, clock.instant());
    }

    public static UUID crea(FineService servizi, Clock clock, String targa, int velocita) {
        return servizi.createFine(di(targa, velocita, clock));
    }

    public static UUID crea(FineService servizi, String targa, int velocita) {
        return crea(servizi, Clock.systemUTC(), targa, velocita);
    }
}
