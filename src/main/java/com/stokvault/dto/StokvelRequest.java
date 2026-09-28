package com.stokvault.dto;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.StokvelType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JSON body for creating or updating a stokvel, e.g.
 * {"name":"Ubuntu Savings Club","type":"ROTATING","contributionAmount":500,
 *  "frequency":"MONTHLY","startDate":"2026-06-01"}
 */
public record StokvelRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull StokvelType type,
        // @Digits: at most 10 digits before the decimal point and 2 after (i.e. cents)
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal contributionAmount,
        @NotNull ContributionFrequency frequency,
        @NotNull LocalDate startDate) {
}
