package com.stokvault.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneNumbersTest {

    @Test
    void differentWritingsOfTheSameNumberNormaliseTheSame() {
        assertEquals("0821234567", PhoneNumbers.normalise("082 123 4567"));
        assertEquals("0821234567", PhoneNumbers.normalise("+27 82 123 4567"));
        assertEquals("0821234567", PhoneNumbers.normalise("27821234567"));
        assertEquals("0821234567", PhoneNumbers.normalise("(082) 123-4567"));
    }

    @Test
    void rejectsNumbersThatArentSouthAfrican() {
        assertNull(PhoneNumbers.normalise("12345"));
        assertNull(PhoneNumbers.normalise("0921234567"));
        assertNull(PhoneNumbers.normalise("+44 20 7946 0958"));
        assertNull(PhoneNumbers.normalise(null));
    }

    @Test
    void displaysInGroups() {
        assertEquals("082 123 4567", PhoneNumbers.display("0821234567"));
    }
}
