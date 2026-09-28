package com.stokvault.service;

import com.stokvault.domain.Money;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.dto.CycleView;
import com.stokvault.entity.ContributionCycle;
import com.stokvault.entity.StokvelGroup;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Money figures for a group, computed in the database with SUM/COUNT queries. Always scoped to
 * one group. Callers do the access checks; this is also used by the batch job, hence @PermitAll.
 */
@Stateless
@PermitAll
public class LedgerService {

    private static final List<VerificationStatus> UNRESOLVED = List.of(VerificationStatus.PENDING, VerificationStatus.PENDING_REVIEW);
    private static final List<PayoutStatus> OPEN_PAYOUTS = List.of(PayoutStatus.SCHEDULED, PayoutStatus.PENDING_APPROVAL, PayoutStatus.CONFIRMED);

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    /** Money the group holds: verified contributions minus payouts actually paid. */
    public BigDecimal balance(StokvelGroup group) {
        return verifiedTotal(group).subtract(paidOut(group));
    }

    public BigDecimal verifiedTotal(StokvelGroup group) {
        return Money.orZero(em.createQuery(
                        "SELECT SUM(c.amount) FROM Contribution c WHERE c.group = :group AND c.verificationStatus = :verified",
                        BigDecimal.class)
                .setParameter("group", group)
                .setParameter("verified", VerificationStatus.VERIFIED)
                .getSingleResult());
    }

    public BigDecimal paidOut(StokvelGroup group) {
        return payoutTotal(group, List.of(PayoutStatus.PAID));
    }

    public BigDecimal openPayoutsTotal(StokvelGroup group) {
        return payoutTotal(group, OPEN_PAYOUTS);
    }

    public long unverifiedCount(StokvelGroup group) {
        return em.createQuery("SELECT COUNT(c) FROM Contribution c WHERE c.group = :group AND c.verificationStatus IN :unresolved", Long.class)
                .setParameter("group", group)
                .setParameter("unresolved", UNRESOLVED)
                .getSingleResult();
    }

    public long unverifiedCount(ContributionCycle cycle) {
        return em.createQuery("SELECT COUNT(c) FROM Contribution c WHERE c.cycle = :cycle AND c.verificationStatus IN :unresolved", Long.class)
                .setParameter("cycle", cycle)
                .setParameter("unresolved", UNRESOLVED)
                .getSingleResult();
    }

    public BigDecimal verifiedTotal(ContributionCycle cycle) {
        return Money.orZero(em.createQuery(
                        "SELECT SUM(c.amount) FROM Contribution c WHERE c.cycle = :cycle AND c.verificationStatus = :verified",
                        BigDecimal.class)
                .setParameter("cycle", cycle)
                .setParameter("verified", VerificationStatus.VERIFIED)
                .getSingleResult());
    }

    /** Verified amount per member in one cycle. */
    public Map<UUID, BigDecimal> verifiedByMember(ContributionCycle cycle) {
        return sumByMember(cycle, List.of(VerificationStatus.VERIFIED));
    }

    /** Amount per member still waiting for verification in one cycle. */
    public Map<UUID, BigDecimal> unresolvedByMember(ContributionCycle cycle) {
        return sumByMember(cycle, UNRESOLVED);
    }

    /** Members expected to pay this cycle: active, and joined on or before its due date. */
    public List<UUID> expectedPayers(ContributionCycle cycle) {
        return em.createQuery("""
                        SELECT ms.member.id FROM Membership ms
                        WHERE ms.group = :group AND ms.status = :active AND ms.joinedDate <= :due""", UUID.class)
                .setParameter("group", cycle.getGroup())
                .setParameter("active", MembershipStatus.ACTIVE)
                .setParameter("due", cycle.getDueDate())
                .getResultList();
    }

    /** How many expected payers have paid the cycle in full (verified). */
    private long paidInFull(ContributionCycle cycle, List<UUID> expected) {
        Map<UUID, BigDecimal> verified = verifiedByMember(cycle);
        return expected.stream()
                .filter(id -> verified.getOrDefault(id, Money.ZERO).compareTo(cycle.getAmountDue()) >= 0)
                .count();
    }

    /** % of expected payers who have paid the cycle in full (verified). */
    public int completionPercent(ContributionCycle cycle) {
        List<UUID> expected = expectedPayers(cycle);
        return expected.isEmpty() ? 0 : (int) (paidInFull(cycle, expected) * 100 / expected.size());
    }

    public CycleView view(ContributionCycle cycle) {
        List<UUID> expected = expectedPayers(cycle);
        long paid = paidInFull(cycle, expected);
        int percent = expected.isEmpty() ? 0 : (int) (paid * 100 / expected.size());
        return new CycleView(cycle.getId(), cycle.getGroup().getId(), cycle.getCycleNumber(), cycle.getDueDate(),
                cycle.getAmountDue(), cycle.getStatus(), percent, verifiedTotal(cycle), unverifiedCount(cycle),
                expected.size(), paid, Money.of(cycle.getAmountDue().multiply(BigDecimal.valueOf(expected.size()))));
    }

    private BigDecimal payoutTotal(StokvelGroup group, List<PayoutStatus> statuses) {
        return Money.orZero(em.createQuery("SELECT SUM(p.amount) FROM Payout p WHERE p.group = :group AND p.status IN :statuses", BigDecimal.class)
                .setParameter("group", group)
                .setParameter("statuses", statuses)
                .getSingleResult());
    }

    private Map<UUID, BigDecimal> sumByMember(ContributionCycle cycle, List<VerificationStatus> statuses) {
        // A query selecting several values returns each row as an Object[]
        return em.createQuery("""
                        SELECT c.member.id, SUM(c.amount) FROM Contribution c
                        WHERE c.cycle = :cycle AND c.verificationStatus IN :statuses
                        GROUP BY c.member.id""", Object[].class)
                .setParameter("cycle", cycle)
                .setParameter("statuses", statuses)
                .getResultStream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> Money.of((BigDecimal) row[1])));
    }
}
