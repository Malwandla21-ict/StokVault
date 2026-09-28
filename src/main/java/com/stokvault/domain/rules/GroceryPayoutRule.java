package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;
import com.stokvault.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * GROCERY: the pooled funds are split equally between all active members (for the year-end
 * bulk shop). Any cents that can't be split evenly stay in the group's balance.
 */
public class GroceryPayoutRule implements PayoutRule {

    @Override
    public GroupType type() {
        return GroupType.GROCERY;
    }

    @Override
    public List<PlannedPayout> plan(PayoutContext context) {
        if (context.members().isEmpty()) {
            throw new IllegalArgumentException("The group has no active members");
        }
        BigDecimal pool = context.amountToDistribute() != null ? context.amountToDistribute() : context.balance();
        if (pool.signum() <= 0) {
            throw new IllegalArgumentException("There is nothing to share out");
        }
        BigDecimal share = pool.divide(BigDecimal.valueOf(context.members().size()), 2, RoundingMode.DOWN);
        if (share.signum() <= 0) {
            throw new IllegalArgumentException("The pool is too small to share between " + context.members().size() + " members");
        }
        return context.members().stream()
                .map(m -> new PlannedPayout(m.memberId(), Money.of(share), "Equal grocery share"))
                .toList();
    }
}
