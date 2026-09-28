package com.stokvault.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Hashes and checks passwords and login codes with Jakarta Security's built-in
 * Pbkdf2PasswordHash (PBKDF2 with HMAC-SHA256, a random salt per hash, many iterations).
 *
 * The SDD mentions bcrypt; PBKDF2 is the algorithm that ships with Jakarta Security, so no
 * third-party library is needed. Like bcrypt, it is slow on purpose, which makes guessing
 * passwords from a stolen database impractical.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Provided by the Jakarta Security implementation (Soteria in Payara)
    @Inject
    private Pbkdf2PasswordHash pbkdf2;

    @PostConstruct
    void configure() {
        pbkdf2.initialize(Map.of(
                "Pbkdf2PasswordHash.Algorithm", "PBKDF2WithHmacSHA256",
                "Pbkdf2PasswordHash.Iterations", "100000",
                "Pbkdf2PasswordHash.SaltSizeBytes", "16",
                "Pbkdf2PasswordHash.KeySizeBytes", "32"));
    }

    public String hash(String secret) {
        return pbkdf2.generate(secret.toCharArray());
    }

    public boolean matches(String secret, String storedHash) {
        return storedHash != null && pbkdf2.verify(secret.toCharArray(), storedHash);
    }

    /** A hash of a random secret nobody knows: an account that can only log in by SMS code. */
    public String unusableHash() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return hash(Base64.getEncoder().encodeToString(random));
    }
}
