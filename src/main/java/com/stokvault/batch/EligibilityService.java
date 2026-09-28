package com.stokvault.batch;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.AuditAction;
import com.stokvault.domain.Eligibility;
import com.stokvault.domain.EligibilityCheck;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.entity.Payout;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.service.LedgerService;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The database side of the eligibility job: finds the payouts to check, gathers the facts for
 * each, and stores the outcome. The rules themselves are in domain.Eligibility.
 * Runs as the system (from the batch job), so no caller role is needed.
 */
@Stateless
@PermitAll
public class EligibilityService {

    /** Result for one payout; Serializable because the batch runtime may hold it between steps. */
    public record Outcome(UUID payoutId, boolean passed, String notes) implements Serializable {
    }

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private LedgerService ledger;

    @Inject
    private AuditService audit;

    /** Scheduled payouts not yet overridden; all groups when groupId is null. */
    public List<UUID> payoutsToCheck(UUID groupId) {
        // (Built conditionally: PostgreSQL can't infer the type of a null UUID parameter)
        var query = em.createQuery("SELECT p.id FROM Payout p WHERE p.status = :scheduled AND p.eligibilityCheck <> :overridden"
                        + (groupId == null ? "" : " AND p.group.id = :groupId") + " ORDER BY p.createdAt", UUID.class)
                .setParameter("scheduled", PayoutStatus.SCHEDULED)
                .setParameter("overridden", EligibilityCheck.OVERRIDDEN);
        if (groupId != null) {
            query.setParameter("groupId", groupId);
        }
        return query.getResultList();
    }

    /** Checks one payout against the rules (read-only). */
    public Outcome evaluate(UUID payoutId) {
        Payout payout = em.find(Payout.class, payoutId);
        StokvelGroup group = payout.getGroup();
        boolean recipientActive = em.createQuery("""
                        SELECT COUNT(ms) FROM Membership ms
                        WHERE ms.group = :group AND ms.member = :member AND ms.status = :active""", Long.class)
                .setParameter("group", group)
                .setParameter("member", payout.getMember())
                .setParameter("active", MembershipStatus.ACTIVE)
                .getSingleResult() > 0;
        Eligibility.Result result = Eligibility.evaluate(new Eligibility.Facts(
                group.isActive(),
                recipientActive,
                payout.getCycle() == null ? null : ledger.completionPercent(payout.getCycle()),
                group.getCompletionThreshold(),
                payout.getCycle() == null ? ledger.unverifiedCount(group) : ledger.unverifiedCount(payout.getCycle()),
                ledger.balance(group),
                payout.getAmount()));
        return new Outcome(payoutId, result.passed(), result.summary());
    }

    /** Stores outcomes. Only changes in the result are written to the audit log, not every nightly re-check. */
    public void record(List<Outcome> outcomes) {
        for (Outcome outcome : outcomes) {
            Payout payout = em.find(Payout.class, outcome.payoutId());
            if (payout == null || payout.getStatus() != PayoutStatus.SCHEDULED
                    || payout.getEligibilityCheck() == EligibilityCheck.OVERRIDDEN) {
                continue; // changed while the job was running
            }
            EligibilityCheck result = outcome.passed() ? EligibilityCheck.PASSED : EligibilityCheck.FAILED;
            boolean changed = result != payout.getEligibilityCheck() || !Objects.equals(outcome.notes(), payout.getEligibilityNotes());
            payout.setEligibilityCheck(result);
            payout.setEligibilityNotes(outcome.notes());
            payout.setEligibilityCheckedAt(LocalDateTime.now());
            if (changed) {
                audit.record(payout.getGroup(), AuditAction.PAYOUT_ELIGIBILITY_CHECKED, "Payout", payout.getId(),
                        Map.of("result", result, "notes", outcome.notes(), "recipient", payout.getMember().getFullName(),
                                "amount", payout.getAmount()));
            }
        }
    }
}
