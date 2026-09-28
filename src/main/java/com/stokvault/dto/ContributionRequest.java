package com.stokvault.dto;

import com.stokvault.domain.PaymentMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recording a contribution, e.g.
 * {"memberId":"...","amount":500,"paymentReference":"FNB-778812","paymentMethod":"EFT"}.
 * memberId defaults to yourself (a member reporting their own payment), cycleId to the group's
 * open cycle, contributionDate to today and paymentMethod to EFT.
 */
public record ContributionRequest(
        UUID memberId,
        UUID cycleId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 50) String paymentReference,
        PaymentMethod paymentMethod,
        @PastOrPresent LocalDate contributionDate) {
}
