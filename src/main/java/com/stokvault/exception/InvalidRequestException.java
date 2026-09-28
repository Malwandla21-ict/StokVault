package com.stokvault.exception;

import jakarta.ejb.ApplicationException;

/**
 * Input that passes the simple field checks but is still invalid, e.g. a 13-digit number that
 * isn't a real South African ID. HTTP 400.
 */
@ApplicationException(rollback = true)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
