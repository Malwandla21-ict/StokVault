package com.stokvault.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Turns Bean Validation failures (@NotBlank, @Email, @Positive...) into a 400 response that
 * lists each problem, e.g. "email: must be a well-formed email address".
 */
// @Provider: registers this class with JAX-RS. An ExceptionMapper<X> is called whenever
// a resource method (or something it calls) throws an X, and its Response is sent instead.
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return toErrorResponse(exception);
    }

    static Response toErrorResponse(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(v -> fieldName(v) + ": " + v.getMessage())
                .sorted()
                .toList();
        return ErrorResponse.of(Response.Status.BAD_REQUEST, "The request has invalid fields", details);
    }

    // The full path looks like "create.arg0.email"; only the last part means anything to a client
    private static String fieldName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name == null ? "request" : name;
    }
}
