package com.stokvault.dto;

import com.stokvault.domain.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The treasurer's decision on a contribution: {"decision":"VERIFIED"} or {"decision":"REJECTED","note":"..."}. */
public record VerificationDecision(@NotNull VerificationStatus decision, @Size(max = 255) String note) {
}
