package com.stokvault.service;

import com.stokvault.domain.Money;
import com.stokvault.domain.PayoutRotation;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.StokvelType;
import com.stokvault.dto.NextPayoutResponse;
import com.stokvault.dto.PayoutRequest;
import com.stokvault.entity.Membership;
import com.stokvault.entity.Payout;
import com.stokvault.entity.Stokvel;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scheduling and paying out money to members.
 */
@Stateless
public class PayoutService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private StokvelService stokvelService;

    @Inject
    private MembershipService membershipService;

    @Inject
    private LedgerService ledgerService;

    public List<Payout> list(Long stokvelId, PayoutStatus status) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        String jpql = "SELECT p FROM Payout p WHERE p.membership.stokvel = :stokvel"
                + (status == null ? "" : " AND p.status = :status")
                + " ORDER BY p.payoutDate DESC, p.id DESC";
        var query = em.createQuery(jpql, Payout.class).setParameter("stokvel", stokvel);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getResultList();
    }

    public Payout find(Long stokvelId, Long payoutId) {
        Payout payout = em.find(Payout.class, payoutId);
        if (payout == null || !payout.getMembership().getStokvel().getId().equals(stokvelId)) {
            throw new ResourceNotFoundException("Payout " + payoutId + " not found in stokvel " + stokvelId);
        }
        return payout;
    }

    public Payout schedule(Long stokvelId, PayoutRequest request) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        stokvelService.requireActive(stokvel);
        Membership recipient = membershipService.findActive(stokvelId, request.memberId());

        Payout payout = new Payout();
        payout.setMembership(recipient);
        payout.setAmount(request.amount());
        payout.setPayoutDate(request.payoutDate() == null ? LocalDate.now() : request.payoutDate());
        payout.setNotes(request.notes());
        payout.setStatus(PayoutStatus.SCHEDULED);
        em.persist(payout);
        return payout;
    }

    /** Marks a scheduled payout as paid, provided the stokvel holds enough money. */
    public Payout pay(Long stokvelId, Long payoutId) {
        // Lock the stokvel first so the balance can't change between checking and paying
        stokvelService.findForUpdate(stokvelId);
        Payout payout = find(stokvelId, payoutId);
        requireScheduled(payout);

        BigDecimal balance = ledgerService.balance(stokvelId);
        if (payout.getAmount().compareTo(balance) > 0) {
            throw new BusinessRuleException("Insufficient funds: the balance is " + Money.format(balance)
                    + " but this payout is " + Money.format(payout.getAmount()));
        }
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidOn(LocalDate.now());
        return payout;
    }

    public Payout cancel(Long stokvelId, Long payoutId) {
        Payout payout = find(stokvelId, payoutId);
        requireScheduled(payout);
        payout.setStatus(PayoutStatus.CANCELLED);
        return payout;
    }

    /**
     * For a ROTATING stokvel: whose turn it is next, and the suggested amount
     * (one contribution from every active member).
     */
    public NextPayoutResponse next(Long stokvelId) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        if (stokvel.getType() != StokvelType.ROTATING) {
            throw new BusinessRuleException("Payout rotation only applies to ROTATING stokvels; "
                    + stokvel.getName() + " is " + stokvel.getType());
        }
        List<Membership> active = membershipService.list(stokvelId, false);
        if (active.isEmpty()) {
            throw new BusinessRuleException(stokvel.getName() + " has no active members");
        }

        // Count each member's payouts so far (cancelled ones don't count as a turn).
        // A JPQL query can return several values per row; each row comes back as an Object[].
        Map<Long, Long> received = em.createQuery("""
                        SELECT p.membership.id, COUNT(p) FROM Payout p
                        WHERE p.membership.stokvel = :stokvel AND p.status <> :cancelled
                        GROUP BY p.membership.id""", Object[].class)
                .setParameter("stokvel", stokvel)
                .setParameter("cancelled", PayoutStatus.CANCELLED)
                .getResultStream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));

        List<PayoutRotation.Slot> slots = active.stream()
                .map(ms -> new PayoutRotation.Slot(ms.getId(), ms.getPayoutPosition(),
                        received.getOrDefault(ms.getId(), 0L)))
                .toList();
        PayoutRotation.Slot slot = PayoutRotation.next(slots).orElseThrow();
        Membership membership = active.stream()
                .collect(Collectors.toMap(Membership::getId, Function.identity()))
                .get(slot.memberId());

        BigDecimal pot = stokvel.getContributionAmount().multiply(BigDecimal.valueOf(active.size()));
        return new NextPayoutResponse(membership.getMember().getId(), membership.getMember().getName(),
                membership.getPayoutPosition(), slot.payoutsReceived(), pot);
    }

    private static void requireScheduled(Payout payout) {
        if (payout.getStatus() != PayoutStatus.SCHEDULED) {
            throw new BusinessRuleException("Payout " + payout.getId() + " is already " + payout.getStatus());
        }
    }
}
