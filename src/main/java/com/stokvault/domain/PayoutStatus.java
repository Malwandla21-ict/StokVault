package com.stokvault.domain;

/**
 * A payout starts SCHEDULED, then becomes PAID (money has left the stokvel) or CANCELLED.
 */
public enum PayoutStatus {
    SCHEDULED,
    PAID,
    CANCELLED
}
