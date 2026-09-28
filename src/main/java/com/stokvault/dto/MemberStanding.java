package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;

import java.math.BigDecimal;

/**
 * One member's line in a stokvel summary.
 * expectedToDate: what they should have paid since joining (contribution amount x periods due).
 * arrears: how far behind they are (never negative; paying ahead doesn't create credit here).
 */
public record MemberStanding(Long memberId, String name, MembershipRole role, boolean active,
                             int payoutPosition, BigDecimal totalContributed, BigDecimal expectedToDate,
                             BigDecimal arrears, BigDecimal totalReceived) {
}
