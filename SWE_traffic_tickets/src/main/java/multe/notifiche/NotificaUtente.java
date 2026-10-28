package multe.notifiche;

import multe.dominio.Evento;
import multe.dominio.Multa;
import multe.dominio.MultaObserver;

/** Avvisa il cittadino per ogni evento della sua multa (per ora stampa a video). */
public class NotificaUtente extends MultaObserver {

    @Override
    protected void onEvento(Multa multa, Evento evento) {
        System.out.println("Notifica a " + multa.getPlate()
            + ": multa " + multa.getId() + " -> " + evento);
    }
}
