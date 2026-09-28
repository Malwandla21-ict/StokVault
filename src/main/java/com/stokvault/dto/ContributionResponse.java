package com.stokvault.dto;

import com.stokvault.domain.PaymentMethod;
import com.stokvault.entity.Contribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A contribution as returned by the API.
 */
public record ContributionResponse(Long id, Long stokvelId, Long memberId, String memberName,
                                   BigDecimal amount, LocalDate contributionDate,
                                   PaymentMethod paymentMethod, String reference, LocalDateTime recordedAt) {

    public static ContributionResponse from(Contribution c) {
        return new ContributionResponse(c.getId(), c.getMembership().getStokvel().getId(),
                c.getMembership().getMember().getId(), c.getMembership().getMember().getName(),
                c.getAmount(), c.getContributionDate(), c.getPaymentMethod(), c.getReference(),
                c.getRecordedAt());
    }
}
