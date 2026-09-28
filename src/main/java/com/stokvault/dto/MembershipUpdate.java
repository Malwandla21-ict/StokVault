package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import jakarta.validation.constraints.Positive;

/**
 * Changing a member's role and/or payout position; leave a field out to keep it.
 * Taking a position another member holds swaps the two.
 */
public record MembershipUpdate(MembershipRole role, @Positive Integer payoutPosition) {
}
