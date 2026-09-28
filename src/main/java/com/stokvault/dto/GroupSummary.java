package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A group's financial position (the dashboard headline figures).
 * balance = verified contributions - paid payouts. Unverified money is shown separately.
 */
public record GroupSummary(GroupView group, BigDecimal balance, BigDecimal totalVerified, BigDecimal totalPaidOut,
                           BigDecimal openPayoutsTotal, long awaitingVerification, BigDecimal totalArrears,
                           CycleView currentCycle, NextPayout nextPayout, List<Standing> members) {

    /** One member's line: what they've paid, what they owe, what they've received. */
    public record Standing(UUID memberId, String name, MembershipRole role, MembershipStatus status, int payoutPosition,
                           BigDecimal totalVerified, BigDecimal arrears, BigDecimal totalReceived) {
    }

    /** ROTATIONAL groups: whose turn is next, an estimate of the pot, and when. */
    public record NextPayout(UUID memberId, String memberName, int payoutPosition, BigDecimal estimatedAmount,
                             LocalDate estimatedDate) {
    }
}
