package com.stokvault.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The automated payout eligibility rules (SDD 4.3). Plain Java so they can be unit tested;
 * the Jakarta Batch job gathers the facts from the database and calls {@link #evaluate}.
 */
public final class Eligibility {

    private Eligibility() {
    }

    /**
     * Facts about one scheduled payout.
     * cycleCompletionPercent is null for payouts not tied to a cycle (burial claims, grocery and
     * investment share-outs); then only the other conditions apply.
     */
    public record Facts(boolean groupActive, boolean recipientActive, Integer cycleCompletionPercent,
                        int completionThresholdPercent, long unverifiedContributions,
                        BigDecimal balance, BigDecimal amount) {
    }

    public record Result(boolean passed, List<String> reasons) {

        public String summary() {
            return passed ? "All checks passed" : String.join("; ", reasons);
        }
    }

    public static Result evaluate(Facts facts) {
        List<String> reasons = new ArrayList<>();
        if (!facts.groupActive()) {
            reasons.add("the group is not active");
        }
        if (!facts.recipientActive()) {
            reasons.add("the recipient's membership is not active");
        }
        if (facts.cycleCompletionPercent() != null
                && facts.cycleCompletionPercent() < facts.completionThresholdPercent()) {
            reasons.add("the cycle is " + facts.cycleCompletionPercent() + "% paid; "
                    + facts.completionThresholdPercent() + "% is required");
        }
        if (facts.unverifiedContributions() > 0) {
            reasons.add(facts.unverifiedContributions() + " contribution(s) still await verification");
        }
        if (facts.amount().compareTo(facts.balance()) > 0) {
            reasons.add("the balance (" + Money.format(facts.balance()) + ") is less than the payout ("
                    + Money.format(facts.amount()) + ")");
        }
        return new Result(reasons.isEmpty(), List.copyOf(reasons));
    }
}
