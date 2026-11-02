package fines.domain;

import fines.observers.Observable;
import fines.observers.Observer;
import fines.plates.PlateRegistry;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unico punto in cui nasce una multa. Gli osservatori registrati qui vengono
 * collegati a ogni nuova multa, così ricevono sia l'emissione sia i cambi di stato.
 * L'intestatario si decide qui, una volta sola: è il proprietario della targa ALL'ISTANTE della rilevazione
 * (non al momento dell'elaborazione: una rilevazione in ritardo dopo una vendita va al vecchio proprietario).
 */
class FineMaker extends Observable {

    private static final long BASE_AMOUNT_CENTS = 15_000;
    private static final long CENTS_PER_KMH_OVER_LIMIT = 1_000;

    private final AtomicInteger idCounter = new AtomicInteger(1);

    private final IdentityMap identityMap;
    private final FineDAO fineDAO;
    private final PlateRegistry plateRegistry;
    private final Clock clock;

    FineMaker(IdentityMap identityMap, FineDAO fineDAO, PlateRegistry plateRegistry, Clock clock) {
        this.identityMap = identityMap;
        this.fineDAO = fineDAO;
        this.plateRegistry = plateRegistry;
        this.clock = clock;
    }

    /**
     * @throws IllegalArgumentException     se la misura non è un'infrazione (entro il limite, margine di misura incluso)
     * @throws UnregisteredPlateException  se la targa non aveva un proprietario all'istante della rilevazione
     */
    int createFine(Detection detection) {
        Objects.requireNonNull(detection);
        if (!detection.isViolation()) {
            throw new IllegalArgumentException("Nessuna infrazione: " + detection.getSpeed()
                + " km/h con limite " + detection.getLimit() + " (margine di misura incluso)");
        }
        int ownerId = plateRegistry.ownerAt(detection.getPlate(), detection.getDetectedAt())
            .orElseThrow(() -> new UnregisteredPlateException(detection.getPlate()));

        int id = idCounter.getAndIncrement();
        LocalDate dueDate = LocalDate.now(clock).plusDays(60);

        Fine fine = new Fine(id, computeAmount(detection), ownerId,
                                dueDate, detection, Issued.getInstance());

        fineDAO.save(fine);          // il DAO tiene una propria copia

        // prima gli osservatori, poi la registrazione: nessuno può agire sulla multa senza che sia osservata
        for (Observer o : getObservers()) {
            fine.addObserver(o);
        }
        identityMap.register(fine);       // l'originale resta nella Identity Map
        fine.announceEmission();

        return id;
    }

    /** L'importo dipende dai km/h oltre il limite di QUELL'autovelox, non da un limite fisso. */
    private long computeAmount(Detection detection) {
        return BASE_AMOUNT_CENTS + (long) detection.getExcess() * CENTS_PER_KMH_OVER_LIMIT;
    }
}
