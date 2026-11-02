package fines.notifications;

import fines.domain.FineEvent;
import fines.domain.Fine;
import fines.domain.FineObserver;

/** Avvisa il cittadino per ogni evento della sua multa (per ora stampa a video). */
public class UserNotifier extends FineObserver {

    @Override
    protected void onEvent(Fine fine, FineEvent event) {
        System.out.println("Notifica a " + fine.getPlate()
            + ": multa " + fine.getId() + " -> " + event);
    }
}
