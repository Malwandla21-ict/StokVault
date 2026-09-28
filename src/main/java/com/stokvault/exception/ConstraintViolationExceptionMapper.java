package com.stokvault.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Bean Validation failures (@NotBlank, @Positive...) become a 400 listing each invalid field.
 * (A more specific mapper than GenericExceptionMapper, so it also wins over the JAX-RS
 * implementation's built-in validation mapper, which returns an empty body.)
 */
// @Provider registers this with JAX-RS; ExceptionMapper<X> handles every X a resource throws
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return ErrorResponse.of(Response.Status.BAD_REQUEST, "The request has invalid fields", Errors.details(exception));
    }
}
