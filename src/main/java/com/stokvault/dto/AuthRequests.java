package com.stokvault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Small request bodies for the /auth and /me endpoints. */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** {"phoneNumber":"082 123 4567"}: send me a login code */
    public record OtpRequest(@NotBlank @Size(max = 20) String phoneNumber) {
    }

    /** currentPassword may be left out only while the account has no password yet */
    public record PasswordChange(String currentPassword,
                                 @NotBlank @Size(min = 8, max = 100, message = "must be at least 8 characters") String newPassword) {
    }
}
