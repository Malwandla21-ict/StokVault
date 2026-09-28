package com.stokvault.dto;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.GroupType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Registering or reconfiguring a group, e.g.
 * {"name":"Ubuntu Savings Club","type":"ROTATIONAL","contributionAmount":500,"frequency":"MONTHLY",
 *  "startDate":"2026-06-01","approvalThreshold":2000,"completionThreshold":100}
 * approvalThreshold defaults to R5000, completionThreshold to 100%.
 */
public record GroupRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull GroupType type,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal contributionAmount,
        @NotNull ContributionFrequency frequency,
        @NotNull LocalDate startDate,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal approvalThreshold,
        @Min(1) @Max(100) Integer completionThreshold,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal benefitAmount) {
}
