package com.stokvault.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributionScheduleTest {

    private static final LocalDate JUNE_1 = LocalDate.of(2026, 6, 1);

    @Test
    void nothingIsDueBeforeTheStartDate() {
        assertEquals(0, ContributionSchedule.periodsDue(ContributionFrequency.MONTHLY, JUNE_1, LocalDate.of(2026, 5, 31)));
    }

    @Test
    void theFirstContributionIsDueOnTheStartDate() {
        assertEquals(1, ContributionSchedule.periodsDue(ContributionFrequency.MONTHLY, JUNE_1, JUNE_1));
        assertEquals(1, ContributionSchedule.periodsDue(ContributionFrequency.WEEKLY, JUNE_1, JUNE_1));
    }

    @Test
    void monthlyCountsOnePeriodPerMonthStarted() {
        assertEquals(1, ContributionSchedule.periodsDue(ContributionFrequency.MONTHLY, JUNE_1, LocalDate.of(2026, 6, 30)));
        assertEquals(2, ContributionSchedule.periodsDue(ContributionFrequency.MONTHLY, JUNE_1, LocalDate.of(2026, 7, 1)));
        assertEquals(4, ContributionSchedule.periodsDue(ContributionFrequency.MONTHLY, JUNE_1, LocalDate.of(2026, 9, 28)));
    }

    @Test
    void weeklyAndFortnightlyCountFromTheStartDate() {
        LocalDate threeWeeksLater = JUNE_1.plusWeeks(3);
        assertEquals(4, ContributionSchedule.periodsDue(ContributionFrequency.WEEKLY, JUNE_1, threeWeeksLater));
        assertEquals(2, ContributionSchedule.periodsDue(ContributionFrequency.FORTNIGHTLY, JUNE_1, threeWeeksLater));
        assertEquals(3, ContributionSchedule.periodsDue(ContributionFrequency.FORTNIGHTLY, JUNE_1, JUNE_1.plusWeeks(4)));
    }

    @Test
    void expectedAmountIsAmountTimesPeriods() {
        BigDecimal expected = ContributionSchedule.expectedAmount(
                new BigDecimal("500.00"), ContributionFrequency.MONTHLY, JUNE_1, LocalDate.of(2026, 9, 28));
        assertEquals(0, new BigDecimal("2000.00").compareTo(expected));
    }
}
