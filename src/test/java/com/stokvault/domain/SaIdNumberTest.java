package com.stokvault.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaIdNumberTest {

    @Test
    void acceptsAValidIdNumber() {
        assertTrue(SaIdNumber.isValid("8001015009087"));
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertFalse(SaIdNumber.isValid("8001015009088"));
    }

    @Test
    void rejectsAnImpossibleBirthDate() {
        // 30 February, with an otherwise correct check digit
        String first12 = "800230500908";
        assertFalse(SaIdNumber.isValid(first12 + SaIdNumber.luhnCheckDigit(first12)));
    }

    @Test
    void rejectsWrongLengthOrLetters() {
        assertFalse(SaIdNumber.isValid("800101500908"));
        assertFalse(SaIdNumber.isValid("80010150090870"));
        assertFalse(SaIdNumber.isValid("8001015OO9087"));
        assertFalse(SaIdNumber.isValid(null));
    }

    @Test
    void checkDigitMakesAnyValidPrefixValid() {
        String first12 = "950509056708";
        assertTrue(SaIdNumber.isValid(first12 + SaIdNumber.luhnCheckDigit(first12)));
    }

    @Test
    void masksAllButTheLastFourDigits() {
        assertEquals("*********9087", SaIdNumber.mask("8001015009087"));
        assertEquals("****", SaIdNumber.mask(null));
    }
}
