package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the payout rule for a group type. The group's type is chosen when it is created,
 * which in turn selects its rule.
 */
public final class PayoutRules {

    private static final Map<GroupType, PayoutRule> RULES = new EnumMap<>(GroupType.class);

    static {
        for (PayoutRule rule : List.of(new RotationalPayoutRule(), new GroceryPayoutRule(),
                new BurialPayoutRule(), new InvestmentPayoutRule())) {
            RULES.put(rule.type(), rule);
        }
    }

    private PayoutRules() {
    }

    public static PayoutRule forType(GroupType type) {
        return RULES.get(type);
    }
}
