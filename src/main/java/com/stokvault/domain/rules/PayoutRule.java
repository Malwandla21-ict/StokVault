package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;

import java.util.List;

/**
 * Strategy pattern (SDD 4.1): each stokvel type decides who gets paid and how much in its own way.
 * The PayoutService picks the rule for the group's type and turns its plan into Payout records.
 */
public interface PayoutRule {

    GroupType type();

    /**
     * Works out the payouts for one payout run. Throws IllegalArgumentException (with a message
     * meant for the treasurer) if the run can't be planned, e.g. a burial run with no claim.
     */
    List<PlannedPayout> plan(PayoutContext context);
}
