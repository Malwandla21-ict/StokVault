package com.stokvault.dto;

import com.stokvault.domain.PayoutStatus;
import com.stokvault.entity.Payout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A payout as returned by the API.
 */
public record PayoutResponse(Long id, Long stokvelId, Long memberId, String memberName, BigDecimal amount,
                             LocalDate payoutDate, PayoutStatus status, LocalDate paidOn, String notes,
                             LocalDateTime createdAt) {

    public static PayoutResponse from(Payout p) {
        return new PayoutResponse(p.getId(), p.getMembership().getStokvel().getId(),
                p.getMembership().getMember().getId(), p.getMembership().getMember().getName(),
                p.getAmount(), p.getPayoutDate(), p.getStatus(), p.getPaidOn(), p.getNotes(),
                p.getCreatedAt());
    }
}
