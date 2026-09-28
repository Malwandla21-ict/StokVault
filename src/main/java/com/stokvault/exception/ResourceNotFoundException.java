package com.stokvault.exception;

import jakarta.ejb.ApplicationException;

/**
 * A requested record doesn't exist (or the caller isn't allowed to know it exists). HTTP 404.
 */
// @ApplicationException: by default an EJB wraps unchecked exceptions in EJBException. This tells
// the container to pass this one through unchanged; rollback = true still undoes the transaction.
@ApplicationException(rollback = true)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
