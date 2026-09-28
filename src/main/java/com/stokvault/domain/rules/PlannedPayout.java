package com.stokvault.domain.rules;

import java.math.BigDecimal;
import java.util.UUID;

/** One payout a rule wants to make. */
public record PlannedPayout(UUID memberId, BigDecimal amount, String note) {
}
