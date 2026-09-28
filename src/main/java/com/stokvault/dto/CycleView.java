package com.stokvault.dto;

import com.stokvault.domain.CycleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A contribution cycle with its progress: completionPercent is the share of members who have
 * paid the cycle in full (verified), verifiedTotal the money actually collected.
 */
public record CycleView(UUID id, UUID groupId, int cycleNumber, LocalDate dueDate, BigDecimal amountDue,
                        CycleStatus status, int completionPercent, BigDecimal verifiedTotal,
                        long awaitingVerification) {
}
