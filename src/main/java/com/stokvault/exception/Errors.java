package com.stokvault.exception;

import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.List;

/**
 * Turns any exception into a message a person can act on. Shared by the REST exception mapper
 * and the Faces pages, so both show the same wording.
 */
public final class Errors {

    private Errors() {
    }

    /** Exceptions thrown inside an EJB can arrive wrapped in EJBException; find the real cause. */
    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof EJBException && !(current instanceof EJBAccessException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** True for the errors caused by the user's request rather than a bug. */
    public static boolean isExpected(Throwable error) {
        Throwable e = unwrap(error);
        return e instanceof BusinessRuleException || e instanceof ResourceNotFoundException
                || e instanceof InvalidRequestException
                || e instanceof AccessDeniedException || e instanceof EJBAccessException
                || e instanceof ConstraintViolationException;
    }

    public static String message(Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof EJBAccessException) {
            return "You don't have permission to do that";
        }
        if (e instanceof ConstraintViolationException violations) {
            return String.join("; ", details(violations));
        }
        if (isExpected(e)) {
            return e.getMessage();
        }
        return "Something went wrong on the server. Please try again.";
    }

    /** "email: must be a well-formed email address", one entry per invalid field. */
    public static List<String> details(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream()
                .map(v -> fieldName(v) + ": " + v.getMessage())
                .sorted()
                .toList();
    }

    // The full path looks like "register.arg0.email"; only the last part means anything to a user
    private static String fieldName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name == null ? "request" : name;
    }
}
