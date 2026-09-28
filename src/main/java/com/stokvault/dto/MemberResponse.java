package com.stokvault.dto;

import com.stokvault.entity.Member;

import java.time.LocalDateTime;

/**
 * A member as returned by the API.
 */
public record MemberResponse(Long id, String name, String email, String phone, LocalDateTime createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName(), member.getEmail(),
                member.getPhone(), member.getCreatedAt());
    }
}
