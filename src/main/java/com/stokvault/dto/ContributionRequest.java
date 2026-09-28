package com.stokvault.dto;

import com.stokvault.domain.PaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JSON body for recording a contribution, e.g.
 * {"memberId":3,"amount":500,"contributionDate":"2026-09-01","paymentMethod":"EFT","reference":"SEP-THANDI"}.
 * contributionDate defaults to today and paymentMethod to CASH.
 */
public record ContributionRequest(
        @NotNull Long memberId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @PastOrPresent LocalDate contributionDate,
        PaymentMethod paymentMethod,
        @Size(max = 100) String reference) {
}
