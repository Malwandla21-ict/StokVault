package com.stokvault.dto;

import java.math.BigDecimal;

/**
 * Whose turn it is in a ROTATING stokvel, and the suggested payout: one contribution
 * from every active member.
 */
public record NextPayoutResponse(Long memberId, String memberName, int payoutPosition,
                                 long payoutsAlreadyReceived, BigDecimal suggestedAmount) {
}
