package com.stokvault.exception;

import jakarta.ejb.ApplicationException;

/**
 * Thrown when a request is well formed but breaks a stokvel rule, e.g. paying out more than
 * the balance. Becomes HTTP 409 Conflict (see GenericExceptionMapper).
 */
@ApplicationException(rollback = true)
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
