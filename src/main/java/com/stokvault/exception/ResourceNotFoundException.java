package com.stokvault.exception;

import jakarta.ejb.ApplicationException;

/**
 * Thrown when a requested record doesn't exist. Becomes HTTP 404 (see GenericExceptionMapper).
 */
// @ApplicationException: by default, an EJB wraps any unchecked exception in an EJBException.
// This annotation tells the container to pass this one through to the caller unchanged
// (so the mapper can see it), and rollback = true still undoes the transaction.
@ApplicationException(rollback = true)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
