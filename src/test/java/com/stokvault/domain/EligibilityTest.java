package com.stokvault.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The automated payout eligibility rules (SDD 4.3).
 */
class EligibilityTest {

    private static Eligibility.Facts facts(boolean groupActive, boolean recipientActive, Integer completion,
                                           long unverified, String balance, String amount) {
        return new Eligibility.Facts(groupActive, recipientActive, completion, 100, unverified,
                new BigDecimal(balance), new BigDecimal(amount));
    }

    @Test
    void passesWhenEverythingIsInOrder() {
        Eligibility.Result result = Eligibility.evaluate(facts(true, true, 100, 0, "2500.00", "2500.00"));
        assertTrue(result.passed());
        assertEquals("All checks passed", result.summary());
    }

    @Test
    void failsWhenTheCycleIsNotFullyPaid() {
        Eligibility.Result result = Eligibility.evaluate(facts(true, true, 80, 0, "5000.00", "2000.00"));
        assertFalse(result.passed());
        assertTrue(result.summary().contains("80% paid"));
    }

    @Test
    void aLowerThresholdCanAllowPartialCycles() {
        Eligibility.Facts f = new Eligibility.Facts(true, true, 80, 75, 0, new BigDecimal("5000"), new BigDecimal("2000"));
        assertTrue(Eligibility.evaluate(f).passed());
    }

    @Test
    void failsWhileContributionsAwaitVerification() {
        assertFalse(Eligibility.evaluate(facts(true, true, 100, 2, "5000.00", "2500.00")).passed());
    }

    @Test
    void failsForAnInactiveRecipientOrGroup() {
        assertFalse(Eligibility.evaluate(facts(true, false, 100, 0, "5000.00", "2500.00")).passed());
        assertFalse(Eligibility.evaluate(facts(false, true, 100, 0, "5000.00", "2500.00")).passed());
    }

    @Test
    void failsWhenTheBalanceIsTooLow() {
        Eligibility.Result result = Eligibility.evaluate(facts(true, true, 100, 0, "1000.00", "2500.00"));
        assertFalse(result.passed());
        assertTrue(result.summary().contains("R1000.00"));
    }

    @Test
    void claimsWithoutACycleSkipTheCompletionRule() {
        assertTrue(Eligibility.evaluate(facts(true, true, null, 0, "2250.00", "1500.00")).passed());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        Eligibility.Result result = Eligibility.evaluate(facts(false, false, 40, 3, "0.00", "100.00"));
        assertEquals(5, result.reasons().size());
    }
}
