package com.stokvault.domain;

import java.time.LocalDate;

/**
 * How often a contribution cycle falls due (SDD: payout_frequency).
 */
public enum ContributionFrequency {
    WEEKLY,
    MONTHLY,
    QUARTERLY;

    /** The due date of the cycle after one due on {@code dueDate}. */
    public LocalDate next(LocalDate dueDate) {
        return switch (this) {
            case WEEKLY -> dueDate.plusWeeks(1);
            case MONTHLY -> dueDate.plusMonths(1);
            case QUARTERLY -> dueDate.plusMonths(3);
        };
    }
}
