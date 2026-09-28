package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import com.stokvault.entity.Membership;

import java.time.LocalDate;

/**
 * A member's place in a stokvel, as returned by the API.
 */
public record MembershipResponse(Long stokvelId, String stokvelName, Long memberId, String memberName,
                                 String memberEmail, MembershipRole role, int payoutPosition,
                                 LocalDate joinedOn, LocalDate leftOn, boolean active) {

    public static MembershipResponse from(Membership ms) {
        return new MembershipResponse(ms.getStokvel().getId(), ms.getStokvel().getName(),
                ms.getMember().getId(), ms.getMember().getName(), ms.getMember().getEmail(),
                ms.getRole(), ms.getPayoutPosition(), ms.getJoinedOn(), ms.getLeftOn(), ms.isActive());
    }
}
