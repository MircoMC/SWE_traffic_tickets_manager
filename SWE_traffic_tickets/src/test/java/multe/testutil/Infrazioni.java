package multe.testutil;

import java.time.Clock;
import multe.dominio.Rilevazione;
import multe.dominio.ServiziMulte;

/** Scorciatoie per i test: un'infrazione rilevata "adesso" da un autovelox con limite 50 km/h. */
public final class Infrazioni {

    public static final String AUTOVELOX = "A1";
    public static final int LIMITE = 50;

    private Infrazioni() {}

    public static Rilevazione di(String targa, int velocita, Clock clock) {
        return new Rilevazione(AUTOVELOX, targa, velocita, LIMITE, clock.instant());
    }

    public static int crea(ServiziMulte servizi, Clock clock, String targa, int velocita) {
        return servizi.creaMulta(di(targa, velocita, clock));
    }

    public static int crea(ServiziMulte servizi, String targa, int velocita) {
        return crea(servizi, Clock.systemUTC(), targa, velocita);
    }
}
