package com.stokvault.dto;

import com.stokvault.domain.MembershipRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adding a registered member to a group, e.g. {"memberId":"...","role":"TREASURER"}.
 * role defaults to MEMBER and joinedDate to today.
 */
public record MembershipRequest(@NotNull UUID memberId, MembershipRole role, @PastOrPresent LocalDate joinedDate) {
}
