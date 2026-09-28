package com.stokvault.domain;

/**
 * The kind of stokvel. This mainly affects how payouts work.
 */
public enum StokvelType {
    /** Members take turns receiving the whole pot, in payout-position order. */
    ROTATING,
    /** Money accumulates and is shared out at an agreed time (often December). */
    SAVINGS,
    /** Pooled money is used to buy groceries in bulk, usually at year end. */
    GROCERY,
    /** Pays out to a member's family to help with funeral costs. */
    BURIAL,
    /** Pooled money is invested and the returns are shared. */
    INVESTMENT
}
