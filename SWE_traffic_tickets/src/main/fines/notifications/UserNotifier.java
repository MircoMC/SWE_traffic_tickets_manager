package fines.notifications;

import fines.domain.FineEvent;
import fines.domain.Fine;
import fines.domain.FineObserver;

/** Avvisa il cittadino per ogni evento della sua multa (per ora stampa a video). */
public class UserNotifier extends FineObserver {

    @Override
    protected void onEvent(Fine fine, FineEvent event) {
        // l'id della multa è riservato: non si scrive nei log
        System.out.println("Notifica a " + fine.getPlate() + ": " + event);
    }
}
