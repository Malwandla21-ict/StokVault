package com.stokvault.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Starting a payout run; which fields matter depends on the group type:
 *  ROTATIONAL: cycleId (defaults to the most recent cycle)
 *  BURIAL:     beneficiaryMemberId (the claim) and notes
 *  GROCERY / INVESTMENT: amountToDistribute (defaults to the whole balance)
 */
public record PayoutRunRequest(
        UUID cycleId,
        UUID beneficiaryMemberId,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal amountToDistribute,
        LocalDate payoutDate,
        @Size(max = 500) String notes) {
}
