package com.stokvault.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line in a group's "Recent activity" feed.
 *
 * @param kind   PAID_IN (a verified contribution) or PAID_OUT (a payout made)
 * @param detail e.g. the payment method, or the payout's note
 */
public record ActivityItem(String kind, String name, BigDecimal amount, String detail, LocalDateTime when) {
}
