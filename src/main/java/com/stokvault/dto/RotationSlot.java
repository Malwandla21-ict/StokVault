package com.stokvault.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One place in a rotational group's payout order for the current round.
 *
 * @param status RECEIVED (paid out this round), IN_PROGRESS (a payout is being processed),
 *               NEXT (first in line), UPCOMING
 * @param date   when it was paid, or roughly when it will be
 */
public record RotationSlot(int order, UUID memberId, String name, int payoutPosition, String status,
                           LocalDate date, BigDecimal amount) {
}
