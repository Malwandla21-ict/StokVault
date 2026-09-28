package com.stokvault.domain;

public enum MembershipStatus {
    ACTIVE,
    /** Left the group. The row is kept so their contribution history remains intact. */
    INACTIVE
}
