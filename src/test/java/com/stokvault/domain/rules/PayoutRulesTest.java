package com.stokvault.domain.rules;

import com.stokvault.domain.GroupType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payout rule strategies (SDD 4.1 / 4.3), one per stokvel type.
 */
class PayoutRulesTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private static PayoutContext.MemberPosition member(UUID id, int position, long received, String contributed) {
        return new PayoutContext.MemberPosition(id, "Member " + position, position, received, new BigDecimal(contributed));
    }

    private static PayoutContext context(List<PayoutContext.MemberPosition> members, String cycleTotal, String balance,
                                         String benefit, UUID beneficiary, String distribute) {
        return new PayoutContext(members, cycleTotal == null ? null : new BigDecimal(cycleTotal), new BigDecimal(balance),
                benefit == null ? null : new BigDecimal(benefit), beneficiary, distribute == null ? null : new BigDecimal(distribute));
    }

    @Test
    void eachTypeHasItsRule() {
        assertInstanceOf(RotationalPayoutRule.class, PayoutRules.forType(GroupType.ROTATIONAL));
        assertInstanceOf(GroceryPayoutRule.class, PayoutRules.forType(GroupType.GROCERY));
        assertInstanceOf(BurialPayoutRule.class, PayoutRules.forType(GroupType.BURIAL));
        assertInstanceOf(InvestmentPayoutRule.class, PayoutRules.forType(GroupType.INVESTMENT));
    }

    // ---- rotational ----

    @Test
    void rotationPaysTheWholeCycleToTheNextPosition() {
        List<PlannedPayout> plan = new RotationalPayoutRule().plan(context(
                List.of(member(A, 2, 0, "0"), member(B, 1, 0, "0"), member(C, 3, 0, "0")), "1500.00", "1500.00", null, null, null));
        assertEquals(1, plan.size());
        assertEquals(B, plan.get(0).memberId());
        assertEquals(new BigDecimal("1500.00"), plan.get(0).amount());
    }

    @Test
    void rotationSkipsMembersWhoHaveHadTheirTurn() {
        List<PlannedPayout> plan = new RotationalPayoutRule().plan(context(
                List.of(member(A, 1, 1, "0"), member(B, 2, 1, "0"), member(C, 3, 0, "0")), "1500.00", "1500.00", null, null, null));
        assertEquals(C, plan.get(0).memberId());
    }

    @Test
    void rotationStartsOverAfterEveryoneHasBeenPaid() {
        List<PlannedPayout> plan = new RotationalPayoutRule().plan(context(
                List.of(member(A, 1, 1, "0"), member(B, 2, 1, "0"), member(C, 3, 1, "0")), "1500.00", "1500.00", null, null, null));
        assertEquals(A, plan.get(0).memberId());
    }

    @Test
    void rotationNeedsVerifiedMoney() {
        assertThrows(IllegalArgumentException.class, () -> new RotationalPayoutRule().plan(context(
                List.of(member(A, 1, 0, "0")), "0.00", "0.00", null, null, null)));
    }

    // ---- grocery ----

    @Test
    void grocerySplitsThePoolEquallyAndKeepsLeftoverCents() {
        List<PlannedPayout> plan = new GroceryPayoutRule().plan(context(
                List.of(member(A, 1, 0, "0"), member(B, 2, 0, "0"), member(C, 3, 0, "0")), null, "1000.00", null, null, null));
        assertEquals(3, plan.size());
        plan.forEach(p -> assertEquals(new BigDecimal("333.33"), p.amount()));
    }

    @Test
    void groceryCanShareOutLessThanTheBalance() {
        List<PlannedPayout> plan = new GroceryPayoutRule().plan(context(
                List.of(member(A, 1, 0, "0"), member(B, 2, 0, "0")), null, "1000.00", null, null, "600.00"));
        plan.forEach(p -> assertEquals(new BigDecimal("300.00"), p.amount()));
    }

    // ---- burial ----

    @Test
    void burialPaysTheBenefitToTheClaimant() {
        List<PlannedPayout> plan = new BurialPayoutRule().plan(context(
                List.of(member(A, 1, 0, "0"), member(B, 2, 0, "0")), null, "5000.00", "1500.00", B, null));
        assertEquals(1, plan.size());
        assertEquals(B, plan.get(0).memberId());
        assertEquals(new BigDecimal("1500.00"), plan.get(0).amount());
    }

    @Test
    void burialNeedsAClaimABenefitAndAMember() {
        BurialPayoutRule rule = new BurialPayoutRule();
        List<PayoutContext.MemberPosition> members = List.of(member(A, 1, 0, "0"));
        assertThrows(IllegalArgumentException.class, () -> rule.plan(context(members, null, "5000", "1500", null, null)));
        assertThrows(IllegalArgumentException.class, () -> rule.plan(context(members, null, "5000", null, A, null)));
        assertThrows(IllegalArgumentException.class, () -> rule.plan(context(members, null, "5000", "1500", B, null)));
    }

    // ---- investment ----

    @Test
    void investmentDistributesProRata() {
        // A put in 3000, B 1000: A gets 75%, B 25% of the R5000 being distributed
        List<PlannedPayout> plan = new InvestmentPayoutRule().plan(context(
                List.of(member(A, 1, 0, "3000.00"), member(B, 2, 0, "1000.00")), null, "4000.00", null, null, "5000.00"));
        assertEquals(new BigDecimal("3750.00"), plan.stream().filter(p -> p.memberId().equals(A)).findFirst().orElseThrow().amount());
        assertEquals(new BigDecimal("1250.00"), plan.stream().filter(p -> p.memberId().equals(B)).findFirst().orElseThrow().amount());
    }

    @Test
    void investmentNeverPaysOutMoreThanTheAmountDistributed() {
        List<PlannedPayout> plan = new InvestmentPayoutRule().plan(context(
                List.of(member(A, 1, 0, "100"), member(B, 2, 0, "100"), member(C, 3, 0, "100")), null, "100.00", null, null, null));
        BigDecimal total = plan.stream().map(PlannedPayout::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(total.compareTo(new BigDecimal("100.00")) <= 0);
    }

    @Test
    void investmentSkipsMembersWhoContributedNothing() {
        List<PlannedPayout> plan = new InvestmentPayoutRule().plan(context(
                List.of(member(A, 1, 0, "500"), member(B, 2, 0, "0")), null, "500.00", null, null, null));
        assertEquals(1, plan.size());
    }
}
