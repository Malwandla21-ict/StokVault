package com.stokvault.domain;

/**
 * A contribution cycle (e.g. "September 2026") moves OPEN -> CLOSED -> RECONCILED.
 * OPEN: contributions can be recorded. CLOSED: no new contributions, but pending ones can still
 * be verified. RECONCILED: every contribution has been verified or rejected; the cycle is final.
 */
public enum CycleStatus {
    OPEN,
    CLOSED,
    RECONCILED
}
