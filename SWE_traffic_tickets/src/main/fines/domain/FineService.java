package fines.domain;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import fines.observers.Observer;
import fines.plates.PlateRegistry;
import fines.users.Judge;

/**
 * Facciata: l'unico punto d'ingresso pubblico al dominio delle multe.
 * IdentityMap, StateManager e FineMaker sono interni al package e non si vedono da fuori:
 * dall'esterno si lavora solo per id e si ricevono solo copie.
 *
 * Questo oggetto è il nucleo FIDATO: lo hanno solo il codice di assemblaggio e il LoginProxy.
 * I client ricevono dal login soltanto ciò che il loro ruolo consente (storico, Cancellation).
 *
 * Il DAO viene aggiornato direttamente dallo StateManager a ogni transizione, non tramite osservatori.
 */
public final class FineService {

    private final IdentityMap identityMap = new IdentityMap();
    private final StateManager stateManager;
    private final FineMaker maker;

    /** Un solo thread: le notifiche arrivano in ordine e mai dentro il lock di una multa. */
    private final ExecutorService notifications = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notifiche-multe");
        t.setDaemon(true);
        return t;
    });

    public FineService(FineDAO fineDAO, PlateRegistry plateRegistry, PaymentGateway payments) {
        this(fineDAO, plateRegistry, payments, Clock.systemUTC());
    }

    public FineService(FineDAO fineDAO, PlateRegistry plateRegistry, PaymentGateway payments, Clock clock) {
        Objects.requireNonNull(fineDAO);
        Objects.requireNonNull(clock);
        this.stateManager = new StateManager(identityMap, Objects.requireNonNull(payments), fineDAO, clock);
        this.maker = new FineMaker(identityMap, fineDAO, Objects.requireNonNull(plateRegistry), clock);
    }

    /**
     * Riceve una misura dell'autovelox e crea la multa. Lo si dà all'SpeedCamera come {@code fineService::createFine}.
     *
     * @return l'id della nuova multa (casuale e non sequenziale: da trattare come riservato, non va scritto nei log)
     * @throws IllegalArgumentException    se la velocità non supera il limite
     * @throws UnregisteredPlateException se la targa non aveva un proprietario all'istante della rilevazione
     */
    public UUID createFine(Detection detection) {
        return maker.createFine(detection);
    }

    /**
     * Può pagare chiunque: conta solo che la multa venga pagata. L'importo viene sempre dalla multa.
     *
     * @return true se pagata, false se il gestore dei pagamenti ha rifiutato
     * @throws IllegalStateException se nello stato attuale la multa non è pagabile
     */
    public boolean pay(UUID fineId) {
        return stateManager.paymentStarting(fineId);
    }

    /** Controlla la scadenza con la data del Clock: il chiamante non può sceglierla. */
    public void checkExpiration(UUID fineId) {
        stateManager.checkExpiration(fineId);
    }

    /** L'annullamento si fa solo tramite l'oggetto Cancellation, che si ottiene qui per un Judge. */
    public Cancellation cancellationFor(Judge judge) {
        return new Cancellation(stateManager, Objects.requireNonNull(judge).getId());
    }

    public FineState getState(UUID fineId) {
        return stateManager.getState(fineId);
    }

    /** Copia indipendente: modificarla non ha alcun effetto sulla multa vera. */
    public Fine getCopy(UUID fineId) {
        Fine copy = identityMap.getCopy(fineId);
        if (copy == null) {
            throw new IllegalArgumentException("Multa inesistente: " + fineId);
        }
        return copy;
    }

    /**
     * L'osservatore riceverà emissione e cambi di stato di tutte le multe create da ora in poi,
     * sempre con una COPIA della multa e in modo ASINCRONO (su un thread dedicato, in ordine).
     * Un'eccezione dell'osservatore non ferma gli altri né l'operazione.
     */
    public void addObserver(Observer observer) {
        maker.addObserver(new ProtectedObserver(observer, notifications));
    }

    /**
     * Aspetta che tutte le notifiche accodate fino a questo momento siano state consegnate.
     * Serve a test, demo e spegnimento ordinato (le notifiche non sono "sincrone" con le operazioni).
     */
    public void awaitNotifications() {
        try {
            notifications.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attesa delle notifiche interrotta", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Notifiche non consegnate", e);
        }
    }
}
