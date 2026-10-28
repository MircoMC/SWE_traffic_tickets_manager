package multe.dominio;

import multe.osservatori.Observable;
import multe.osservatori.Observer;
import multe.targhe.RegistroTarghe;

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
class MulteMaker extends Observable {

    private static final long IMPORTO_BASE_CENT = 15_000;
    private static final long CENT_PER_KMH_IN_ECCESSO = 1_000;

    private final AtomicInteger contatoreId = new AtomicInteger(1);

    private final MapperId mapperId;
    private final MultaDAO multaDAO;
    private final RegistroTarghe registroTarghe;
    private final Clock clock;

    MulteMaker(MapperId mapperId, MultaDAO multaDAO, RegistroTarghe registroTarghe, Clock clock) {
        this.mapperId = mapperId;
        this.multaDAO = multaDAO;
        this.registroTarghe = registroTarghe;
        this.clock = clock;
    }

    /**
     * @throws IllegalArgumentException     se la misura non è un'infrazione (entro il limite, margine di misura incluso)
     * @throws TargaNonRegistrataException  se la targa non aveva un proprietario all'istante della rilevazione
     */
    int createMulta(Rilevazione rilevazione) {
        Objects.requireNonNull(rilevazione);
        if (!rilevazione.isInfrazione()) {
            throw new IllegalArgumentException("Nessuna infrazione: " + rilevazione.getVelocita()
                + " km/h con limite " + rilevazione.getLimite() + " (margine di misura incluso)");
        }
        int idIntestatario = registroTarghe.proprietarioAl(rilevazione.getTarga(), rilevazione.getRilevataIl())
            .orElseThrow(() -> new TargaNonRegistrataException(rilevazione.getTarga()));

        int id = contatoreId.getAndIncrement();
        LocalDate scadenza = LocalDate.now(clock).plusDays(60);

        Multa multa = new Multa(id, calcolaImporto(rilevazione), idIntestatario,
                                scadenza, rilevazione, Emessa.getInstance());

        multaDAO.salva(multa);          // il DAO tiene una propria copia

        // prima gli osservatori, poi la registrazione: nessuno può agire sulla multa senza che sia osservata
        for (Observer o : getObservers()) {
            multa.addObserver(o);
        }
        mapperId.register(multa);       // l'originale resta nella Identity Map
        multa.announceEmission();

        return id;
    }

    /** L'importo dipende dai km/h oltre il limite di QUELL'autovelox, non da un limite fisso. */
    private long calcolaImporto(Rilevazione rilevazione) {
        return IMPORTO_BASE_CENT + (long) rilevazione.getEccesso() * CENT_PER_KMH_IN_ECCESSO;
    }
}
