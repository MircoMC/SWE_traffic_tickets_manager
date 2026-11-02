package fines.domain;

/** Eventi che gli osservatori possono ricevere da una multa. */
public enum FineEvent {
    ISSUED, PAID, EXPIRED, CANCELLED
}
