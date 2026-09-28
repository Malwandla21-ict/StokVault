package com.stokvault.service;

import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.StokvelStatus;
import com.stokvault.dto.StokvelRequest;
import com.stokvault.entity.Stokvel;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;

/**
 * Creating, changing, closing and deleting stokvels.
 */
@Stateless
public class StokvelService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    public List<Stokvel> findAll(StokvelStatus status) {
        if (status == null) {
            return em.createQuery("SELECT s FROM Stokvel s ORDER BY s.name", Stokvel.class).getResultList();
        }
        return em.createQuery("SELECT s FROM Stokvel s WHERE s.status = :status ORDER BY s.name", Stokvel.class)
                .setParameter("status", status)
                .getResultList();
    }

    public Stokvel find(Long id) {
        Stokvel stokvel = em.find(Stokvel.class, id);
        if (stokvel == null) {
            throw new ResourceNotFoundException("Stokvel " + id + " not found");
        }
        return stokvel;
    }

    /**
     * Loads the stokvel and locks its row until the transaction ends (SELECT ... FOR UPDATE).
     * Used before anything that checks the balance and then changes it, so two payouts
     * processed at the same moment can't both spend the same money.
     */
    public Stokvel findForUpdate(Long id) {
        Stokvel stokvel = em.find(Stokvel.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (stokvel == null) {
            throw new ResourceNotFoundException("Stokvel " + id + " not found");
        }
        return stokvel;
    }

    public void requireActive(Stokvel stokvel) {
        if (!stokvel.isActive()) {
            throw new BusinessRuleException(stokvel.getName() + " is closed");
        }
    }

    public Stokvel create(StokvelRequest request) {
        Stokvel stokvel = new Stokvel();
        apply(stokvel, request);
        em.persist(stokvel);
        return stokvel;
    }

    public Stokvel update(Long id, StokvelRequest request) {
        Stokvel stokvel = find(id);
        apply(stokvel, request);
        return stokvel;
    }

    /** Closing keeps all history but stops new members, contributions and payouts. */
    public Stokvel close(Long id) {
        Stokvel stokvel = find(id);
        requireActive(stokvel);
        Long scheduled = em.createQuery("""
                        SELECT COUNT(p) FROM Payout p
                        WHERE p.membership.stokvel = :stokvel AND p.status = :status""", Long.class)
                .setParameter("stokvel", stokvel)
                .setParameter("status", PayoutStatus.SCHEDULED)
                .getSingleResult();
        if (scheduled > 0) {
            throw new BusinessRuleException(stokvel.getName() + " has " + scheduled
                    + " scheduled payout(s). Pay or cancel them before closing");
        }
        stokvel.setStatus(StokvelStatus.CLOSED);
        stokvel.setClosedOn(LocalDate.now());
        return stokvel;
    }

    public Stokvel reopen(Long id) {
        Stokvel stokvel = find(id);
        if (stokvel.isActive()) {
            throw new BusinessRuleException(stokvel.getName() + " is already active");
        }
        stokvel.setStatus(StokvelStatus.ACTIVE);
        stokvel.setClosedOn(null);
        return stokvel;
    }

    /** Only a stokvel with no money history can be deleted; otherwise close it. */
    public void delete(Long id) {
        Stokvel stokvel = find(id);
        Long contributions = em.createQuery(
                        "SELECT COUNT(c) FROM Contribution c WHERE c.membership.stokvel = :stokvel", Long.class)
                .setParameter("stokvel", stokvel)
                .getSingleResult();
        Long payouts = em.createQuery(
                        "SELECT COUNT(p) FROM Payout p WHERE p.membership.stokvel = :stokvel", Long.class)
                .setParameter("stokvel", stokvel)
                .getSingleResult();
        if (contributions > 0 || payouts > 0) {
            throw new BusinessRuleException(stokvel.getName()
                    + " has contributions or payouts on record. Close it instead of deleting it");
        }
        // A bulk JPQL DELETE: removes the memberships in one statement
        em.createQuery("DELETE FROM Membership ms WHERE ms.stokvel = :stokvel")
                .setParameter("stokvel", stokvel)
                .executeUpdate();
        em.remove(stokvel);
    }

    private void apply(Stokvel stokvel, StokvelRequest request) {
        String name = request.name().trim();
        List<Stokvel> sameName = em.createQuery(
                        "SELECT s FROM Stokvel s WHERE LOWER(s.name) = LOWER(:name)", Stokvel.class)
                .setParameter("name", name)
                .getResultList();
        if (sameName.stream().anyMatch(other -> !other.getId().equals(stokvel.getId()))) {
            throw new BusinessRuleException("A stokvel called " + name + " already exists");
        }
        stokvel.setName(name);
        stokvel.setDescription(request.description());
        stokvel.setType(request.type());
        stokvel.setContributionAmount(request.contributionAmount());
        stokvel.setFrequency(request.frequency());
        stokvel.setStartDate(request.startDate());
    }
}
