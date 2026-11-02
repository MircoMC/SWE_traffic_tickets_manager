package fines.payments;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    @Test
    void paymentIsIdempotentPerFine() {
        PaymentService service = new PaymentService();
        assertTrue(service.payment(1, 5_000));
        assertTrue(service.payment(1, 5_000));   // already charged: true, no new charge
    }

    @Test
    void invalidAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentService().payment(1, 0));
    }
}
