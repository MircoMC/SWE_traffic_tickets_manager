package fines.domain;

import java.util.UUID;

/**
 * Port to the payment provider, defined by the domain and implemented outside.
 *
 * CONTRACT: {@code payment} must be IDEMPOTENT per fineId. If the charge for that id
 * has already happened, it must return true without charging again. This is needed because, if saving
 * fails after the charge, the caller can repeat pay() without charging twice.
 */
public interface PaymentGateway {

    /** @return true if the fine is charged (now or previously), false if the payment is refused */
    boolean payment(UUID fineId, long amountCents);
}
