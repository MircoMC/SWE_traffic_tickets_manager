package fines.domain;

import fines.observers.Observer;
import fines.plates.PlateRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The only place where a fine is born. The owner is decided here, once: it is the owner of the plate
 * AT THE INSTANT OF THE DETECTION (not when it is processed: a late detection after a sale goes to the old owner).
 * The fine is saved first, so the database is always the source of truth.
 */
class FineMaker {

    private static final long BASE_AMOUNT_CENTS = 15_000;
    private static final long CENTS_PER_KMH_OVER_LIMIT = 1_000;

    private final IdentityMap identityMap;
    private final FineDAO fineDAO;
    private final PlateRegistry plateRegistry;
    private final Clock clock;
    private final Consumer<Fine> attachObservers;
    private final List<Observer> ownObservers = new CopyOnWriteArrayList<>();

    FineMaker(IdentityMap identityMap, FineDAO fineDAO, PlateRegistry plateRegistry, Clock clock) {
        this(identityMap, fineDAO, plateRegistry, clock, fine -> { });
    }

    /** Observers of this maker only (FineService registers them for all fines through attachObservers). */
    void addObserver(Observer observer) {
        ownObservers.add(observer);
    }

    FineMaker(IdentityMap identityMap, FineDAO fineDAO, PlateRegistry plateRegistry, Clock clock,
              Consumer<Fine> attachObservers) {
        this.identityMap = identityMap;
        this.fineDAO = fineDAO;
        this.plateRegistry = plateRegistry;
        this.clock = clock;
        this.attachObservers = attachObservers;
    }

    /**
     * @throws IllegalArgumentException    if the detection is not a violation (measurement margin included)
     * @throws UnregisteredPlateException  if the plate had no owner at the instant of the detection
     */
    UUID createFine(Detection detection) {
        Objects.requireNonNull(detection);
        if (!detection.isViolation()) {
            throw new IllegalArgumentException("No violation: " + detection.getSpeed()
                + " km/h with limit " + detection.getLimit() + " (measurement margin included)");
        }
        int ownerId = plateRegistry.ownerAt(detection.getPlate(), detection.getDetectedAt())
            .orElseThrow(() -> new UnregisteredPlateException(detection.getPlate()));

        UUID id = UUID.randomUUID();
        LocalDate dueDate = LocalDate.now(clock).plusDays(60);
        Fine fine = new Fine(id, computeAmount(detection), ownerId, dueDate, detection, Issued.getInstance());

        fineDAO.save(fine);              // 1) durable truth
        attachObservers.accept(fine);    // 2) nobody can act on the fine without it being observed
        ownObservers.forEach(fine::addObserver);
        synchronized (fine.lock) {       // nobody can act on the fine before ISSUED is announced
            identityMap.register(fine);  // 3) cache
            fine.announceEmission();     // 4) notify
        }
        return id;
    }

    /** The amount depends on the km/h over the limit of THAT speed camera. */
    private long computeAmount(Detection detection) {
        return BASE_AMOUNT_CENTS + (long) detection.getExcess() * CENTS_PER_KMH_OVER_LIMIT;
    }
}
