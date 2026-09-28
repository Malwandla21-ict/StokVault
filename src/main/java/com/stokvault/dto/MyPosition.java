package com.stokvault.dto;

import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.MembershipRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The member dashboard (SDD 6.1): for each of my groups, my history, current position and my
 * next expected payout.
 *
 * @param paidCurrentCycle   true when my contribution to the open cycle is verified in full
 * @param queuePosition      rotational groups: how many payouts until it's my turn (1 = next)
 * @param nextPayoutEstimate rotational groups: roughly when my turn comes
 */
public record MyPosition(UUID groupId, String groupName, GroupType type, GroupStatus groupStatus, MembershipRole role,
                         BigDecimal groupBalance, BigDecimal myVerifiedTotal, BigDecimal myArrears,
                         long myAwaitingVerification, BigDecimal myReceived, LocalDate currentCycleDue,
                         BigDecimal currentCycleAmount, boolean paidCurrentCycle, int payoutPosition,
                         Integer queuePosition, LocalDate nextPayoutEstimate) {
}
