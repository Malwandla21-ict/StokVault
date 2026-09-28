package com.stokvault.domain;

/**
 * A CLOSED stokvel keeps its history but accepts no new members, contributions or payouts.
 */
public enum StokvelStatus {
    ACTIVE,
    CLOSED
}
