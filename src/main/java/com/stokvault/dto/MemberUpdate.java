package com.stokvault.dto;

import com.stokvault.domain.NotificationChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changing a member's contact details. The ID number can't be changed. */
public record MemberUpdate(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 20) String phoneNumber,
        @Email @Size(max = 120) String email,
        NotificationChannel preferredChannel) {
}
