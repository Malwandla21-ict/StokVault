package com.stokvault.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyAndFrequencyTest {

    @Test
    void moneyAlwaysHasTwoDecimals() {
        assertEquals("500.00", Money.of(new BigDecimal("500")).toPlainString());
        assertEquals("0.00", Money.orZero(null).toPlainString());
        assertEquals("R1500.50", Money.format(new BigDecimal("1500.5")));
    }

    @Test
    void cyclesFollowOnByFrequency() {
        LocalDate first = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 7), ContributionFrequency.WEEKLY.next(first));
        assertEquals(LocalDate.of(2026, 2, 28), ContributionFrequency.MONTHLY.next(first));
        assertEquals(LocalDate.of(2026, 4, 30), ContributionFrequency.QUARTERLY.next(first));
    }

    @Test
    void statusHelpers() {
        assertTrue(VerificationStatus.PENDING.isUnresolved());
        assertTrue(VerificationStatus.PENDING_REVIEW.isUnresolved());
        assertFalse(VerificationStatus.VERIFIED.isUnresolved());
        assertTrue(PayoutStatus.CONFIRMED.isOpen());
        assertFalse(PayoutStatus.PAID.isOpen());
        assertTrue(EligibilityCheck.OVERRIDDEN.allowsProgress());
        assertFalse(EligibilityCheck.FAILED.allowsProgress());
    }
}
