package com.stokvault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A required explanation, e.g. for overriding a failed eligibility check: {"reason":"..."} */
public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
