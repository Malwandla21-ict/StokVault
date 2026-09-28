package com.stokvault.dto;

import jakarta.validation.constraints.Size;

/** An optional explanation, e.g. {"reason":"Dispute under investigation"}; the body may be left out. */
public record OptionalReason(@Size(max = 500) String reason) {

    public static String of(OptionalReason body) {
        return body == null ? null : body.reason();
    }
}
