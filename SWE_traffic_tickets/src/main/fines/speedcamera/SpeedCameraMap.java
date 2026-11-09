package fines.speedcamera;

import java.time.Instant;
import java.util.List;

/**
 * Il mapper degli autovelox: dato l'id di un autovelox, ne dà la STORIA delle configurazioni
 * (posizione e limite, ciascuna valida da un certo istante). La storia, e non solo il valore attuale,
 * serve per giudicare anche una misura arrivata in ritardo con il limite che valeva quando è stata fatta,
 * e per i cambi programmati nel futuro.
 */
public interface SpeedCameraMap {

    /**
     * @return le configurazioni ordinate per inizio crescente; vuota se l'autovelox è sconosciuto
     * @throws RuntimeException se la sorgente non è raggiungibile o i dati non sono validi
     */
    List<Period> history(String cameraId);

    /**
     * Programma un cambio di configurazione che parte dall'istante {@code dal}. La storia è APPEND-ONLY verso il
     * futuro: {@code dal} deve essere successivo a adesso, così nessun istante già trascorso cambia mai
     * configurazione, e non può coincidere con l'inizio di un periodo già programmato (non si sovrascrive).
     * Chi lo chiama deve avere il permesso di amministratore: vedi SpeedCameraManagement.
     *
     * @throws IllegalArgumentException se {@code dal} non è nel futuro
     * @throws IllegalStateException    se esiste già una configurazione con lo stesso inizio
     */
    void schedule(String cameraId, Instant from, Configuration configuration);
}
