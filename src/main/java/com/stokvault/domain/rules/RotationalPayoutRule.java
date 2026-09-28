package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;
import com.stokvault.domain.Money;

import java.util.Comparator;
import java.util.List;

/**
 * ROTATIONAL: the whole cycle's pot goes to one member, taking turns in payout-position order.
 * The next recipient is whoever has received the fewest payouts so far (ties go to the earliest
 * position), so once everyone has had a turn a new round starts from the front.
 */
public class RotationalPayoutRule implements PayoutRule {

    /** Fewest payouts received first, then lowest payout position: the order members get paid in. */
    public static final Comparator<PayoutContext.MemberPosition> ROTATION_ORDER =
            Comparator.comparingLong(PayoutContext.MemberPosition::payoutsReceived)
                    .thenComparingInt(PayoutContext.MemberPosition::payoutPosition);

    @Override
    public GroupType type() {
        return GroupType.ROTATIONAL;
    }

    @Override
    public List<PlannedPayout> plan(PayoutContext context) {
        if (context.cycleVerifiedTotal() == null || context.cycleVerifiedTotal().signum() <= 0) {
            throw new IllegalArgumentException("The cycle has no verified contributions to pay out");
        }
        PayoutContext.MemberPosition next = nextRecipient(context.members());
        return List.of(new PlannedPayout(next.memberId(), Money.of(context.cycleVerifiedTotal()),
                "Rotation payout to position " + next.payoutPosition()));
    }

    public static PayoutContext.MemberPosition nextRecipient(List<PayoutContext.MemberPosition> members) {
        return members.stream()
                .min(ROTATION_ORDER)
                .orElseThrow(() -> new IllegalArgumentException("The group has no active members"));
    }
}
