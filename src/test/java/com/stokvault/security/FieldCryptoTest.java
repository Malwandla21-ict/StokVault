package com.stokvault.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * National ID encryption at rest (SDD 7.3).
 */
class FieldCryptoTest {

    private static byte[] key(int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return key;
    }

    private final FieldCrypto crypto = new FieldCrypto(key(7));

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String encrypted = crypto.encrypt("8001015009087");
        assertFalse(encrypted.contains("8001015009087"));
        assertEquals("8001015009087", crypto.decrypt(encrypted));
    }

    @Test
    void theSameValueEncryptsDifferentlyEachTime() {
        assertNotEquals(crypto.encrypt("8001015009087"), crypto.encrypt("8001015009087"));
    }

    @Test
    void lookupHashIsStableSoUniquenessCanBeEnforced() {
        assertEquals(crypto.lookupHash("8001015009087"), crypto.lookupHash("8001015009087"));
        assertEquals(64, crypto.lookupHash("8001015009087").length());
    }

    @Test
    void anotherKeyCanNotReadOrMatchTheData() {
        FieldCrypto other = new FieldCrypto(key(8));
        String encrypted = crypto.encrypt("8001015009087");
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
        assertNotEquals(crypto.lookupHash("8001015009087"), other.lookupHash("8001015009087"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        char[] chars = crypto.encrypt("8001015009087").toCharArray();
        chars[20] = chars[20] == 'A' ? 'B' : 'A';
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(new String(chars)));
    }

    @Test
    void theKeyMustBe256Bits() {
        assertThrows(IllegalArgumentException.class, () -> new FieldCrypto(new byte[16]));
    }
}
