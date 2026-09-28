package com.stokvault.dto;

import com.stokvault.domain.NotificationChannel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registering a new member. The member then signs in with an SMS code and chooses a password.
 * e.g. {"fullName":"Thandi Mokoena","nationalId":"8001015009087","phoneNumber":"082 123 4567",
 *       "email":"thandi@example.com","popiaConsent":true}
 */
public record MemberRegistration(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = "\\d{13}", message = "must be a 13-digit South African ID number") String nationalId,
        @NotBlank @Size(max = 20) String phoneNumber,
        @Email @Size(max = 120) String email,
        NotificationChannel preferredChannel,
        // POPIA: processing personal information needs the member's consent (SDD 7.4)
        @NotNull @AssertTrue(message = "the member must consent to StokVault processing their information (POPIA)")
        Boolean popiaConsent) {
}
