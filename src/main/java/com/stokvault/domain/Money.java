package com.stokvault.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for rand amounts, which always have exactly 2 decimal places (cents).
 */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() {
    }

    /** The amount with exactly 2 decimal places, so 500 and 500.0 both become 500.00. */
    public static BigDecimal of(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** For messages, e.g. R1500.00 */
    public static String format(BigDecimal amount) {
        return "R" + of(amount).toPlainString();
    }
}
