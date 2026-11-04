package fines.domain;

import fines.observers.Observable;
import fines.observers.Observer;
import fines.plates.PlateRegistry;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Unico punto in cui nasce una multa. Gli osservatori registrati qui vengono
 * collegati a ogni nuova multa, così ricevono sia l'emissione sia i cambi di stato.
 * L'intestatario si decide qui, una volta sola: è il proprietario della targa ALL'ISTANTE della rilevazione
 */
class FineMaker extends Observable {

    private static final long BASE_AMOUNT_CENTS = 15_000;
    private static final long CENTS_PER_KMH_OVER_LIMIT = 1_000;

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
     * @throws IllegalArgumentException     se la misura non è un'infrazione
     * @throws UnregisteredPlateException  se la targa non aveva un proprietario
     */
    UUID createFine(Detection detection) {
        Objects.requireNonNull(detection);
        if (!detection.isViolation()) {
            throw new IllegalArgumentException("Nessuna infrazione: " + detection.getSpeed()
                    + " km/h con limite " + detection.getLimit() + " (margine di misura incluso)");
        }
        int ownerId = plateRegistry.ownerAt(detection.getPlate(), detection.getDetectedAt())
                .orElseThrow(() -> new UnregisteredPlateException(detection.getPlate()));

        UUID id = UUID.randomUUID();
        // Manteniamo la compatibilità con Fine (che usa un UUID id) tramite hashCode


        LocalDate dueDate = LocalDate.now(clock).plusDays(60);

        Fine fine = new Fine(id, computeAmount(detection), ownerId,
                dueDate, detection, Issued.getInstance());

        fineDAO.save(fine);

        for (Observer o : getObservers()) {
            fine.addObserver(o);
        }

        synchronized (fine.lock) {
            identityMap.register(fine);
            fine.announceEmission();
        }

        return id;
    }

    private long computeAmount(Detection detection) {
        return BASE_AMOUNT_CENTS + (long) detection.getExcess() * CENTS_PER_KMH_OVER_LIMIT;
    }
}
