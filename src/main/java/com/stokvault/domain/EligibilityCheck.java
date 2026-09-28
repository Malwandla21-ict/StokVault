package com.stokvault.domain;

/**
 * Outcome of the automated payout eligibility check (SDD 4.3), set by the Jakarta Batch job.
 * NOT_RUN is the state before the job has looked at a new payout.
 */
public enum EligibilityCheck {
    NOT_RUN,
    PASSED,
    FAILED,
    /** A committee member accepted a FAILED check anyway, with a recorded reason. */
    OVERRIDDEN;

    public boolean allowsProgress() {
        return this == PASSED || this == OVERRIDDEN;
    }
}
