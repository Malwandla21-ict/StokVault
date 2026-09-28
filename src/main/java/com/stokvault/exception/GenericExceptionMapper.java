package com.stokvault.exception;

import jakarta.json.bind.JsonbException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catch-all mapper so every API error reaches the client as the same JSON shape (ErrorResponse)
 * instead of an HTML error page, and no internal details leak out.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        Throwable error = Errors.unwrap(exception);

        if (error instanceof ResourceNotFoundException) {
            return ErrorResponse.of(Response.Status.NOT_FOUND, error.getMessage());
        }
        if (error instanceof BusinessRuleException) {
            return ErrorResponse.of(Response.Status.CONFLICT, error.getMessage());
        }
        if (error instanceof InvalidRequestException) {
            return ErrorResponse.of(Response.Status.BAD_REQUEST, error.getMessage());
        }
        if (error instanceof AccessDeniedException) {
            return ErrorResponse.of(Response.Status.FORBIDDEN, error.getMessage());
        }
        if (Errors.isPermissionDenied(error)) {
            // @RolesAllowed on an EJB method refused the caller
            return ErrorResponse.of(Response.Status.FORBIDDEN, "You don't have permission to do that");
        }
        if (error instanceof ConstraintViolationException violations) {
            return ErrorResponse.of(Response.Status.BAD_REQUEST, "The request has invalid fields", Errors.details(violations));
        }
        if (error instanceof WebApplicationException web) {
            // JAX-RS's own errors (unknown URL = 404, wrong method = 405...): keep the status
            Response original = web.getResponse();
            return original.hasEntity() ? original : ErrorResponse.of(original.getStatusInfo(), web.getMessage());
        }
        if (error instanceof JsonbException || error instanceof ProcessingException) {
            return ErrorResponse.of(Response.Status.BAD_REQUEST, "The request body could not be read as JSON for this endpoint");
        }

        LOG.log(Level.SEVERE, "Unhandled exception", exception);
        return ErrorResponse.of(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
