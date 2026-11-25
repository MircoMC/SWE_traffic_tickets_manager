package fines.notifications;

import fines.domain.FineEvent;
import fines.domain.Fine;
import fines.domain.FineObserver;

/** Notifies the citizen of every event of their fine (for now it prints to screen). */
public class UserNotifier extends FineObserver {

    @Override
    protected void onEvent(Fine fine, FineEvent event) {
        System.out.println("Notifica a " + fine.getPlate() + ": " + event);
    }
}
