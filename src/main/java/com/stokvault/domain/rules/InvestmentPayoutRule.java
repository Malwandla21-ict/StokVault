package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;
import com.stokvault.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * INVESTMENT: the amount being distributed (the pool plus any returns) is shared pro rata:
 * each member gets the same fraction of it as the fraction of all contributions they made.
 * Shares are rounded down to the cent, so the total never exceeds the amount distributed.
 */
public class InvestmentPayoutRule implements PayoutRule {

    @Override
    public GroupType type() {
        return GroupType.INVESTMENT;
    }

    @Override
    public List<PlannedPayout> plan(PayoutContext context) {
        BigDecimal pool = context.amountToDistribute() != null ? context.amountToDistribute() : context.balance();
        if (pool.signum() <= 0) {
            throw new IllegalArgumentException("There is nothing to distribute");
        }
        BigDecimal totalContributed = context.members().stream()
                .map(PayoutContext.MemberPosition::verifiedContributions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalContributed.signum() <= 0) {
            throw new IllegalArgumentException("No verified contributions yet, so there are no shares to calculate");
        }
        return context.members().stream()
                .filter(m -> m.verifiedContributions().signum() > 0)
                .map(m -> {
                    BigDecimal share = pool.multiply(m.verifiedContributions())
                            .divide(totalContributed, 2, RoundingMode.DOWN);
                    BigDecimal percent = m.verifiedContributions().multiply(BigDecimal.valueOf(100))
                            .divide(totalContributed, 1, RoundingMode.HALF_UP);
                    return new PlannedPayout(m.memberId(), Money.of(share), "Pro-rata share (" + percent + "%)");
                })
                .filter(p -> p.amount().signum() > 0)
                .toList();
    }
}
