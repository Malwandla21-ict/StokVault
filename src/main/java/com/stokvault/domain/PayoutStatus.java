package com.stokvault.domain;

/**
 * Payout lifecycle (SDD 4.3), plus CANCELLED:
 * SCHEDULED -> (treasurer confirms, eligibility PASSED/OVERRIDDEN)
 *   -> PENDING_APPROVAL (above the group's approval threshold; a committee member must approve)
 *   -> CONFIRMED -> PAID (money leaves the group; balance must cover it).
 */
public enum PayoutStatus {
    SCHEDULED,
    PENDING_APPROVAL,
    CONFIRMED,
    PAID,
    CANCELLED;

    public boolean isOpen() {
        return this == SCHEDULED || this == PENDING_APPROVAL || this == CONFIRMED;
    }
}
