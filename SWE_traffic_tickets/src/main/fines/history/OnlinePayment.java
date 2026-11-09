package fines.history;

import java.util.Objects;
import java.util.UUID;
import fines.domain.FineService;

/**
 * Pagamento di una multa dalla sessione, passando solo l'id.
 * Il costruttore è package-private: lo crea solo il LoginProxy.
 *
 * SCELTA DI PROGETTO: non verifica di chi sia la multa. La sessione mostra all'utente solo le sue multe
 * e da lì le paga; se un client scritto ad arte passa l'id di un'altra multa, il risultato è che
 * qualcuno paga una multa non sua, e non lo consideriamo un rischio. L'importo e le regole di stato
 * restano comunque decisi dal dominio (FineService.pay).
 */
public final class OnlinePayment {

    private final FineService services;

    OnlinePayment(FineService services) {
        this.services = Objects.requireNonNull(services);
    }

    /**
     * @return true se pagata, false se il gestore dei pagamenti ha rifiutato
     * @throws IllegalStateException    se nello stato attuale la multa non è pagabile
     * @throws IllegalArgumentException se la multa non esiste
     */
    public boolean pay(UUID fineId) {
        return services.pay(fineId);
    }
}
