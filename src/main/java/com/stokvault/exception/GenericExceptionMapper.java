package com.stokvault.exception;

import jakarta.ejb.EJBException;
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
 * Catch-all mapper, so every error reaches the client as the same JSON shape (ErrorResponse)
 * instead of Payara's HTML error page.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        Throwable error = unwrap(exception);

        if (error instanceof ResourceNotFoundException) {
            return ErrorResponse.of(Response.Status.NOT_FOUND, error.getMessage());
        }
        if (error instanceof BusinessRuleException) {
            return ErrorResponse.of(Response.Status.CONFLICT, error.getMessage());
        }
        if (error instanceof ConstraintViolationException violations) {
            return ConstraintViolationExceptionMapper.toErrorResponse(violations);
        }
        if (error instanceof WebApplicationException web) {
            // JAX-RS's own errors (unknown URL = 404, wrong HTTP method = 405...): keep the status
            Response original = web.getResponse();
            if (original.hasEntity()) {
                return original;
            }
            return ErrorResponse.of(original.getStatusInfo(), web.getMessage());
        }
        if (error instanceof JsonbException || error instanceof ProcessingException) {
            // The body wasn't valid JSON, or had e.g. text where a number or enum value belongs
            return ErrorResponse.of(Response.Status.BAD_REQUEST,
                    "The request body could not be read as JSON for this endpoint");
        }

        // Anything else is a bug: log the details on the server, don't leak them to the client
        LOG.log(Level.SEVERE, "Unhandled exception", exception);
        return ErrorResponse.of(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    // Runtime exceptions thrown inside an EJB (e.g. a JPA validation failure) reach us wrapped in
    // EJBException; dig out the original cause so it can be mapped properly.
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof EJBException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
