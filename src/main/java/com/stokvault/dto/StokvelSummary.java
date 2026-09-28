package com.stokvault.dto;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.StokvelStatus;
import com.stokvault.domain.StokvelType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A stokvel's financial position: money in, money out, what's left, and who's behind.
 * balance = totalContributions - totalPaidOut. Scheduled payouts are shown separately
 * because they haven't left the account yet.
 */
public record StokvelSummary(Long stokvelId, String name, StokvelType type, StokvelStatus status,
                             BigDecimal contributionAmount, ContributionFrequency frequency,
                             long activeMembers, BigDecimal totalContributions, BigDecimal totalPaidOut,
                             BigDecimal totalScheduled, BigDecimal balance, BigDecimal totalArrears,
                             LocalDate asOf, List<MemberStanding> members) {
}
