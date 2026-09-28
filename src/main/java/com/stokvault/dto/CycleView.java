package com.stokvault.dto;

import com.stokvault.domain.CycleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A contribution cycle with its progress.
 *
 * @param expectedPayers    members expected to pay this cycle (active, joined by the due date)
 * @param paidInFull        of those, how many have paid in full (verified)
 * @param completionPercent paidInFull as a percentage of expectedPayers
 * @param target            what the cycle should collect: amountDue x expectedPayers
 */
public record CycleView(UUID id, UUID groupId, int cycleNumber, LocalDate dueDate, BigDecimal amountDue,
                        CycleStatus status, int completionPercent, BigDecimal verifiedTotal,
                        long awaitingVerification, long expectedPayers, long paidInFull, BigDecimal target) {

    /** Collected as a percentage of the target (for progress bars), 0-100. */
    public int collectedPercent() {
        if (target == null || target.signum() <= 0) {
            return 0;
        }
        return Math.min(100, verifiedTotal.multiply(BigDecimal.valueOf(100)).divide(target, 0, java.math.RoundingMode.DOWN).intValue());
    }

    public long outstandingPayers() {
        return Math.max(0, expectedPayers - paidInFull);
    }

    public BigDecimal stillToCome() {
        return target.subtract(verifiedTotal).max(BigDecimal.ZERO);
    }
}
