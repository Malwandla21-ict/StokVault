package com.stokvault.domain;

/**
 * Verification state of a contribution (SDD 4.2). Only VERIFIED money counts towards balances.
 */
public enum VerificationStatus {
    /** Submitted by the member themselves; waiting for the treasurer to confirm it. */
    PENDING,
    /** Confirmed by the treasurer. */
    VERIFIED,
    /** Flagged automatically (amount mismatch or reused reference) for the treasurer to resolve. */
    PENDING_REVIEW,
    /** The treasurer decided this payment did not happen or was captured wrongly. */
    REJECTED;

    public boolean isUnresolved() {
        return this == PENDING || this == PENDING_REVIEW;
    }
}
