package com.stokvault.security;

import jakarta.security.enterprise.credential.Credential;

/**
 * A one-time login code sent by SMS (SDD 7.1: OTP login for members who struggle to remember
 * passwords). Validated by StokVaultIdentityStore like any other credential.
 */
public record OtpCredential(String phoneNumber, String code) implements Credential {
}
