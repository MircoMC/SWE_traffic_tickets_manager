package fines.domain;

/** Events that observers can receive from a fine. */
public enum FineEvent {
    ISSUED, PAID, EXPIRED, CANCELLED
}
