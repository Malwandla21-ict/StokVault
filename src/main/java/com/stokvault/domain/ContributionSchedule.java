package com.stokvault.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Works out how much a member should have contributed by a given date.
 * This is plain Java with no Jakarta EE annotations, so it can be unit tested without a server
 * (see ContributionScheduleTest).
 */
public final class ContributionSchedule {

    private ContributionSchedule() {
    }

    /**
     * How many contributions fall due from {@code start} up to and including {@code asOf}.
     * The first contribution is due on the start date itself, then one every period after that.
     */
    public static long periodsDue(ContributionFrequency frequency, LocalDate start, LocalDate asOf) {
        if (asOf.isBefore(start)) {
            return 0;
        }
        return switch (frequency) {
            case WEEKLY -> ChronoUnit.WEEKS.between(start, asOf) + 1;
            case FORTNIGHTLY -> ChronoUnit.WEEKS.between(start, asOf) / 2 + 1;
            case MONTHLY -> ChronoUnit.MONTHS.between(start, asOf) + 1;
        };
    }

    /** The total a member should have paid in by {@code asOf}. */
    public static BigDecimal expectedAmount(BigDecimal amountPerPeriod, ContributionFrequency frequency,
                                            LocalDate start, LocalDate asOf) {
        return amountPerPeriod.multiply(BigDecimal.valueOf(periodsDue(frequency, start, asOf)));
    }
}
