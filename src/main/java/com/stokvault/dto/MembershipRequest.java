package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * JSON body for adding a member to a stokvel, e.g. {"memberId":3,"role":"TREASURER"}.
 * role defaults to MEMBER and joinedOn to today. Set joinedOn to an earlier date
 * when capturing an existing stokvel's history.
 */
public record MembershipRequest(
        @NotNull Long memberId,
        MembershipRole role,
        @PastOrPresent LocalDate joinedOn) {
}
