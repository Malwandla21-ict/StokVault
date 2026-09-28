package com.stokvault.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * Encrypts sensitive fields at rest (SDD 7.3: national ID numbers, AES-256) and computes lookup
 * hashes for them.
 *
 * - encrypt/decrypt: AES-256-GCM with a random IV per value, so the same ID encrypts differently
 *   every time. That's safe, but it means the database can't enforce uniqueness on the ciphertext...
 * - ...so lookupHash() gives a keyed HMAC-SHA256 of the value, which IS the same every time.
 *   The UNIQUE constraint is on that column instead, and it reveals nothing without the key.
 *
 * The 256-bit master key comes from the system property stokvault.crypto.key (Base64), or, if that
 * isn't set, from a key file created on first use in the Payara domain's config folder.
 * Both derived keys come from the master key, so there is only one secret to protect and back up.
 */
public final class FieldCrypto {

    private static final Logger LOG = Logger.getLogger(FieldCrypto.class.getName());
    private static final String KEY_PROPERTY = "stokvault.crypto.key";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile FieldCrypto instance;

    private final SecretKeySpec encryptionKey;
    private final byte[] lookupKey;

    /** For tests: build from an explicit 32-byte master key. */
    public FieldCrypto(byte[] masterKey) {
        if (masterKey.length != 32) {
            throw new IllegalArgumentException("The master key must be 32 bytes (256 bits)");
        }
        this.encryptionKey = new SecretKeySpec(hmac(masterKey, "stokvault-field-encryption"), "AES");
        this.lookupKey = hmac(masterKey, "stokvault-field-lookup");
    }

    public static FieldCrypto get() {
        FieldCrypto current = instance;
        if (current == null) {
            synchronized (FieldCrypto.class) {
                if (instance == null) {
                    instance = new FieldCrypto(loadMasterKey());
                }
                current = instance;
            }
        }
        return current;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key or tampered ciphertext: GCM's authentication tag doesn't match
            throw new IllegalStateException("Could not decrypt a protected field (wrong key or altered data)", e);
        }
    }

    /** Deterministic keyed hash for uniqueness checks and lookups (hex, 64 characters). */
    public String lookupHash(String value) {
        return HexFormat.of().formatHex(hmac(lookupKey, value));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available in the JDK", e);
        }
    }

    private static byte[] loadMasterKey() {
        String configured = System.getProperty(KEY_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Base64.getDecoder().decode(configured.trim());
        }
        String instanceRoot = System.getProperty("com.sun.aas.instanceRoot");
        Path keyFile = instanceRoot != null
                ? Path.of(instanceRoot, "config", "stokvault-field.key")
                : Path.of(System.getProperty("user.home"), ".stokvault", "stokvault-field.key");
        try {
            if (Files.exists(keyFile)) {
                return Base64.getDecoder().decode(Files.readString(keyFile).trim());
            }
            byte[] key = new byte[32];
            RANDOM.nextBytes(key);
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(key));
            LOG.warning("Created a new field-encryption key at " + keyFile
                    + ". Back it up: without it, encrypted national ID numbers can't be read.");
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read or create the encryption key file " + keyFile, e);
        }
    }
}
