package com.stokvault.dto;

import com.stokvault.domain.ContributionFrequency;
import com.stokvault.domain.StokvelStatus;
import com.stokvault.domain.StokvelType;
import com.stokvault.entity.Stokvel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A stokvel as returned by the API.
 */
public record StokvelResponse(Long id, String name, String description, StokvelType type,
                              BigDecimal contributionAmount, ContributionFrequency frequency,
                              LocalDate startDate, StokvelStatus status, LocalDate closedOn,
                              LocalDateTime createdAt) {

    public static StokvelResponse from(Stokvel s) {
        return new StokvelResponse(s.getId(), s.getName(), s.getDescription(), s.getType(),
                s.getContributionAmount(), s.getFrequency(), s.getStartDate(), s.getStatus(),
                s.getClosedOn(), s.getCreatedAt());
    }
}
