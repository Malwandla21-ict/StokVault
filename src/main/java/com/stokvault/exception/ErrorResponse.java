package com.stokvault.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * The JSON body of every error response, e.g.
 * {"status":409,"error":"Conflict","message":"Insufficient funds...","details":[]}
 */
public record ErrorResponse(int status, String error, String message, List<String> details) {

    public static Response of(Response.StatusType status, String message) {
        return of(status, message, List.of());
    }

    public static Response of(Response.StatusType status, String message, List<String> details) {
        ErrorResponse body = new ErrorResponse(status.getStatusCode(), status.getReasonPhrase(), message, details);
        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON).build();
    }
}
