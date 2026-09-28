package com.stokvault.service;

import com.stokvault.domain.Money;
import com.stokvault.domain.PaymentMethod;
import com.stokvault.dto.ContributionRequest;
import com.stokvault.entity.Contribution;
import com.stokvault.entity.Membership;
import com.stokvault.entity.Stokvel;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recording and correcting the money members pay in.
 */
@Stateless
public class ContributionService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private StokvelService stokvelService;

    @Inject
    private MembershipService membershipService;

    @Inject
    private LedgerService ledgerService;

    /** Newest first. memberId, from and to are optional filters (null = no filter). */
    public List<Contribution> list(Long stokvelId, Long memberId, LocalDate from, LocalDate to) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        StringBuilder jpql = new StringBuilder("SELECT c FROM Contribution c WHERE c.membership.stokvel = :stokvel");
        if (memberId != null) {
            jpql.append(" AND c.membership.member.id = :memberId");
        }
        if (from != null) {
            jpql.append(" AND c.contributionDate >= :from");
        }
        if (to != null) {
            jpql.append(" AND c.contributionDate <= :to");
        }
        jpql.append(" ORDER BY c.contributionDate DESC, c.id DESC");

        TypedQuery<Contribution> query = em.createQuery(jpql.toString(), Contribution.class)
                .setParameter("stokvel", stokvel);
        if (memberId != null) {
            query.setParameter("memberId", memberId);
        }
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        return query.getResultList();
    }

    public Contribution find(Long stokvelId, Long contributionId) {
        Contribution contribution = em.find(Contribution.class, contributionId);
        if (contribution == null || !contribution.getMembership().getStokvel().getId().equals(stokvelId)) {
            throw new ResourceNotFoundException("Contribution " + contributionId + " not found in stokvel " + stokvelId);
        }
        return contribution;
    }

    public Contribution record(Long stokvelId, ContributionRequest request) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        stokvelService.requireActive(stokvel);
        Membership membership = membershipService.findActive(stokvelId, request.memberId());

        LocalDate date = request.contributionDate() == null ? LocalDate.now() : request.contributionDate();
        if (date.isBefore(stokvel.getStartDate())) {
            throw new BusinessRuleException("Contribution date " + date + " is before " + stokvel.getName()
                    + " started on " + stokvel.getStartDate());
        }

        Contribution contribution = new Contribution();
        contribution.setMembership(membership);
        contribution.setAmount(request.amount());
        contribution.setContributionDate(date);
        contribution.setPaymentMethod(request.paymentMethod() == null ? PaymentMethod.CASH : request.paymentMethod());
        contribution.setReference(request.reference());
        em.persist(contribution);
        return contribution;
    }

    /**
     * Removes a contribution captured by mistake. Refused if that money has already been
     * paid out, i.e. if removing it would push the balance below zero.
     */
    public void delete(Long stokvelId, Long contributionId) {
        stokvelService.findForUpdate(stokvelId);
        Contribution contribution = find(stokvelId, contributionId);
        BigDecimal balanceAfter = ledgerService.balance(stokvelId).subtract(contribution.getAmount());
        if (balanceAfter.signum() < 0) {
            throw new BusinessRuleException("Can't delete this " + Money.format(contribution.getAmount())
                    + " contribution: the money has already been paid out (balance would become "
                    + Money.format(balanceAfter) + ")");
        }
        em.remove(contribution);
    }
}
