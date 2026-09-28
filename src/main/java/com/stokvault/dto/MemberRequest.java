package com.stokvault.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON body for creating or updating a member, e.g.
 * {"name":"Thandi Mokoena","email":"thandi@example.com","phone":"0821234567"}
 *
 * DTOs (Data Transfer Objects) are the shapes the API accepts and returns. Keeping them
 * separate from the entities means clients can't set fields they shouldn't (like id or
 * createdAt), and the database design can change without breaking the API.
 * A Java record is a compact class for exactly this: fields, a constructor and getters in one line.
 */
public record MemberRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        // Optional. Digits and spaces, with an optional leading +, e.g. 0821234567 or +27 82 123 4567
        @Pattern(regexp = "^\\+?[0-9 ]{9,15}$", message = "must be a phone number like 0821234567 or +27 82 123 4567")
        String phone) {
}
