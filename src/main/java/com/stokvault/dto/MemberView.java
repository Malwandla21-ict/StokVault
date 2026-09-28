package com.stokvault.dto;

import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.PhoneNumbers;
import com.stokvault.domain.SaIdNumber;
import com.stokvault.entity.Member;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A member as the API shows them. The national ID is always masked (only the last 4 digits).
 */
public record MemberView(UUID id, String fullName, String phoneNumber, String email, String nationalIdMasked,
                         NotificationChannel preferredChannel, boolean admin, boolean passwordSet,
                         LocalDateTime createdAt) {

    public static MemberView from(Member m) {
        return new MemberView(m.getId(), m.getFullName(), PhoneNumbers.display(m.getPhoneNumber()), m.getEmail(),
                SaIdNumber.mask(m.getNationalId()), m.getPreferredChannel(), m.isAdmin(), m.isPasswordSet(),
                m.getCreatedAt());
    }
}
