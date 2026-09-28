package com.stokvault.domain;

/**
 * A member's role within one stokvel. The same person can hold different roles in different stokvels.
 */
public enum MembershipRole {
    CHAIRPERSON,
    TREASURER,
    SECRETARY,
    MEMBER;

    /** Office-bearer roles: a stokvel can have only one active member in each of these. */
    public boolean isOffice() {
        return this != MEMBER;
    }
}
