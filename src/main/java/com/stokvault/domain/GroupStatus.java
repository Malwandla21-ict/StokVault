package com.stokvault.domain;

/**
 * Lifecycle of a stokvel group (SDD 4.1).
 * DRAFT: being set up; members can be added but no money moves.
 * ACTIVE: cycles, contributions and payouts are allowed.
 * SUSPENDED: temporarily frozen (e.g. during a dispute); history stays readable.
 * CLOSED: finished for good.
 */
public enum GroupStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
