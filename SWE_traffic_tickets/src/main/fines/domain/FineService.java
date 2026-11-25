package fines.domain;

import fines.observers.Observer;
import fines.plates.PlateRegistry;
import fines.users.Judge;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Facade: the only public entry point to the fine's domain.
 * IdentityMap, StateManager and FineMaker are package-private: from outside you work by id and receive copies.
 *
 * This object is the TRUSTED core: only the assembly code and LoginProxy hold it.
 * Clients get from the login only what their role allows (history, Cancellation).
 *
 * The DAO is the source of truth and is updated by the StateManager at every transition.
 * Observers registered here are attached to every fine created or loaded afterwards.
 */
public final class FineService {

    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private final IdentityMap identityMap;
    private final StateManager stateManager;
    private final FineMaker maker;

    public FineService(FineDAO fineDAO, PlateRegistry plateRegistry, PaymentGateway payments) {
        this(fineDAO, plateRegistry, payments, Clock.systemDefaultZone());
    }

    public FineService(FineDAO fineDAO, PlateRegistry plateRegistry, PaymentGateway payments, Clock clock) {
        Objects.requireNonNull(fineDAO);
        Objects.requireNonNull(clock);
        Consumer<Fine> attachObservers = fine -> observers.forEach(fine::addObserver);
        this.identityMap = new IdentityMap(fineDAO, attachObservers);
        this.stateManager = new StateManager(identityMap, Objects.requireNonNull(payments), fineDAO, clock);
        this.maker = new FineMaker(identityMap, fineDAO, Objects.requireNonNull(plateRegistry), clock,
                                   attachObservers);
    }

    private final ExecutorService notifications = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notifiche-multe");
        t.setDaemon(true);
        return t;
    });


    /**
     * Receives a detection from the speed camera and creates the fine
     * (handed to the camera as a function: {@code service::createFine}).
     *
     * @return the id of the new fine
     * @throws IllegalArgumentException       if the speed does not exceed the limit
     * @throws UnregisteredPlateException     if the plate had no owner at the instant of the detection
     */
    public UUID createFine(Detection detection) {
        return maker.createFine(detection);
    }

    /**
     * Anyone can pay: all that matters is that the fine gets paid. The amount always comes from the fine.
     *
     * @return true if paid, false if the payment gateway refused
     * @throws IllegalStateException if in the current state the fine cannot be paid
     */
    public boolean pay(UUID fineId) {
        return stateManager.paymentStarting(fineId);
    }

    /** Checks the expiration with the Clock date: the caller cannot choose it. */
    public void checkExpiration(UUID fineId) {
        stateManager.checkExpiration(fineId);
    }

    /** Cancelling is only possible through the Cancellation object, obtained here for a Judge. */
    public Cancellation cancellationFor(Judge judge) {
        return new Cancellation(stateManager, Objects.requireNonNull(judge).getId());
    }

    public FineState getState(UUID fineId) {
        return stateManager.getState(fineId);
    }

    /** Independent copy: changing it has no effect on the real fine. */
    public Fine getCopy(UUID fineId) {
        Fine copy = identityMap.getCopy(fineId);
        if (copy == null) {
            throw new IllegalArgumentException("Multa inesistente: " + fineId);
        }
        return copy;
    }
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
    /**
     * The observer receives issue and state changes of every fine created or loaded from now on,
     * always with a COPY of the fine. An exception in an observer stops neither the others nor the operation.
     */
    public void addObserver(Observer observer) {
        observers.add(new ProtectedObserver(observer, notifications));
    }
}
