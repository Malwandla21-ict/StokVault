package com.stokvault.exception;

import jakarta.ejb.ApplicationException;

/**
 * A well-formed request that breaks a stokvel rule, e.g. paying out more than the balance.
 * HTTP 409 Conflict. The message is written for the person using the system.
 */
@ApplicationException(rollback = true)
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
