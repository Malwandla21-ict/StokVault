package com.stokvault.service;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Checks whether the database is reachable, for the /api/health endpoint.
 */
@Stateless
public class HealthService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    // @TransactionAttribute(NOT_SUPPORTED): run this method without a transaction.
    // A failed query inside a transaction marks it "rollback only", and the container would
    // then throw when the method returns, even though we caught the error ourselves.
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean isDatabaseUp() {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
