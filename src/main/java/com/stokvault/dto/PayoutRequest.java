package com.stokvault.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JSON body for scheduling a payout, e.g.
 * {"memberId":3,"amount":2500,"payoutDate":"2026-09-30","notes":"September rotation"}.
 * payoutDate defaults to today.
 */
public record PayoutRequest(
        @NotNull Long memberId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        LocalDate payoutDate,
        @Size(max = 500) String notes) {
}
