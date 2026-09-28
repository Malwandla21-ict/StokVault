package com.stokvault.service;

import com.stokvault.domain.Money;
import com.stokvault.domain.PayoutStatus;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;

/**
 * Money totals for a stokvel, calculated in the database with SUM queries.
 */
@Stateless
public class LedgerService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    public BigDecimal totalContributions(Long stokvelId) {
        BigDecimal total = em.createQuery(
                        "SELECT SUM(c.amount) FROM Contribution c WHERE c.membership.stokvel.id = :id",
                        BigDecimal.class)
                .setParameter("id", stokvelId)
                .getSingleResult();
        return orZero(total);
    }

    public BigDecimal totalPayouts(Long stokvelId, PayoutStatus status) {
        BigDecimal total = em.createQuery("""
                        SELECT SUM(p.amount) FROM Payout p
                        WHERE p.membership.stokvel.id = :id AND p.status = :status""", BigDecimal.class)
                .setParameter("id", stokvelId)
                .setParameter("status", status)
                .getSingleResult();
        return orZero(total);
    }

    /** Money currently held: everything paid in minus everything actually paid out. */
    public BigDecimal balance(Long stokvelId) {
        return totalContributions(stokvelId).subtract(totalPayouts(stokvelId, PayoutStatus.PAID));
    }

    // SUM over zero rows is NULL in SQL, not 0
    static BigDecimal orZero(BigDecimal value) {
        return value == null ? Money.ZERO : Money.of(value);
    }
}
