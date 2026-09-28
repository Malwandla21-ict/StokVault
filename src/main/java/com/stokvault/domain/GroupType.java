package com.stokvault.domain;

/**
 * Stokvel types from the SDD (section 5.2). Each type has its own payout rule
 * (see com.stokvault.domain.rules).
 */
public enum GroupType {
    /** The pooled contribution goes in full to one member per cycle, rotating through the members. */
    ROTATIONAL,
    /** Pooled funds are shared out for bulk household purchases, usually at year end. */
    GROCERY,
    /** Pays a fixed benefit to a member's household when a death is claimed, not on a schedule. */
    BURIAL,
    /** Pooled funds (plus returns) are distributed in proportion to what each member put in. */
    INVESTMENT
}
