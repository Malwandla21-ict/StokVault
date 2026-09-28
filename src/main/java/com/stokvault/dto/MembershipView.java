package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.PhoneNumbers;
import com.stokvault.entity.Membership;

import java.time.LocalDate;
import java.util.UUID;

public record MembershipView(UUID id, UUID groupId, String groupName, UUID memberId, String memberName,
                             String phoneNumber, MembershipRole role, MembershipStatus status, int payoutPosition,
                             LocalDate joinedDate, LocalDate leftDate) {

    public static MembershipView from(Membership ms) {
        return new MembershipView(ms.getId(), ms.getGroup().getId(), ms.getGroup().getName(), ms.getMember().getId(),
                ms.getMember().getFullName(), PhoneNumbers.display(ms.getMember().getPhoneNumber()), ms.getRole(),
                ms.getStatus(), ms.getPayoutPosition(), ms.getJoinedDate(), ms.getLeftDate());
    }
}
