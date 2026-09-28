package com.stokvault.domain;

/**
 * A member's role inside one group (SDD 5.2). Roles are per group: the same person can be
 * TREASURER of one stokvel and an ordinary MEMBER of another.
 */
public enum MembershipRole {
    /** Records and verifies contributions, runs and pays out payouts. One active treasurer per group. */
    TREASURER,
    /** Elected committee: approves high-value payouts, overrides failed eligibility checks, manages roles. */
    COMMITTEE,
    /** Contributes, views their own history and payout position. */
    MEMBER
}
