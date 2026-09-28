package com.stokvault.dto;

import com.stokvault.exception.InvalidRequestException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.validation.constraints.Size;

/**
 * An optional explanation, e.g. {"reason":"Dispute under investigation"}. The body may be left out
 * entirely, which a normal JSON body parameter doesn't allow, so resources read the raw text and
 * call {@link #parse}.
 */
public record OptionalReason(@Size(max = 500) String reason) {

    private static final Jsonb JSONB = JsonbBuilder.create();

    /** The reason from an optional JSON body, or null when there's no body. */
    public static String parse(String body) {
        OptionalReason parsed = parseBody(body, OptionalReason.class);
        if (parsed == null || parsed.reason() == null || parsed.reason().isBlank()) {
            return null;
        }
        if (parsed.reason().length() > 500) {
            throw new InvalidRequestException("The reason can be at most 500 characters");
        }
        return parsed.reason().trim();
    }

    /** Parses an optional JSON body: blank means "not sent" (null). */
    public static <T> T parseBody(String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSONB.fromJson(body, type);
        } catch (JsonbException e) {
            throw new InvalidRequestException("The request body could not be read as JSON for this endpoint");
        }
    }
}
