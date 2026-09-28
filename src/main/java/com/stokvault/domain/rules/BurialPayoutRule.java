package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;
import com.stokvault.domain.Money;

import java.util.List;

/**
 * BURIAL: triggered by a claim event rather than a schedule. The fixed benefit configured on
 * the group is paid to the member whose household suffered the loss.
 */
public class BurialPayoutRule implements PayoutRule {

    @Override
    public GroupType type() {
        return GroupType.BURIAL;
    }

    @Override
    public List<PlannedPayout> plan(PayoutContext context) {
        if (context.beneficiaryId() == null) {
            throw new IllegalArgumentException("A burial payout needs a claim: choose the member the claim is for");
        }
        if (context.benefitAmount() == null || context.benefitAmount().signum() <= 0) {
            throw new IllegalArgumentException("Set the group's burial benefit amount before processing claims");
        }
        boolean isMember = context.members().stream().anyMatch(m -> m.memberId().equals(context.beneficiaryId()));
        if (!isMember) {
            throw new IllegalArgumentException("The claim must be for an active member of the group");
        }
        return List.of(new PlannedPayout(context.beneficiaryId(), Money.of(context.benefitAmount()), "Burial claim benefit"));
    }
}
