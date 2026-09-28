package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import jakarta.validation.constraints.Positive;

/**
 * JSON body for changing a member's role or payout position, e.g. {"payoutPosition":2}.
 * Leave a field out to keep its current value. Moving to a position someone else holds
 * swaps the two members.
 */
public record MembershipUpdateRequest(MembershipRole role, @Positive Integer payoutPosition) {
}
