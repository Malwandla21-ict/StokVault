package com.stokvault.exception;

import jakarta.ejb.ApplicationException;

/**
 * The caller is logged in but their role in this group doesn't allow the action. HTTP 403.
 * (Coarse role checks are done by @RolesAllowed; this is for per-group checks, e.g. being
 * TREASURER of this particular group.)
 */
@ApplicationException(rollback = true)
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
