package com.stokvault.domain;

/**
 * Every action that changes a group's configuration, membership or money is written to the
 * hash-chained audit log with one of these actions.
 */
public enum AuditAction {
    GROUP_CREATED,
    GROUP_UPDATED,
    GROUP_STATUS_CHANGED,
    MEMBER_ADDED,
    MEMBER_UPDATED,
    MEMBER_REMOVED,
    CYCLE_OPENED,
    CYCLE_CLOSED,
    CYCLE_RECONCILED,
    CONTRIBUTION_RECORDED,
    CONTRIBUTION_VERIFIED,
    CONTRIBUTION_REJECTED,
    PAYOUT_SCHEDULED,
    PAYOUT_ELIGIBILITY_CHECKED,
    PAYOUT_ELIGIBILITY_OVERRIDDEN,
    PAYOUT_SUBMITTED_FOR_APPROVAL,
    PAYOUT_CONFIRMED,
    PAYOUT_APPROVED,
    PAYOUT_PAID,
    PAYOUT_CANCELLED
}
