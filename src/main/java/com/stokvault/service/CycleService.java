package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.AuditAction;
import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.Money;
import com.stokvault.domain.MembershipRole;
import com.stokvault.dto.CycleGrid;
import com.stokvault.dto.CycleView;
import com.stokvault.entity.ContributionCycle;
import com.stokvault.entity.Membership;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contribution cycles: opening, closing and reconciling them, and the "who has paid" grid.
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class CycleService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private GroupService groups;

    @Inject
    private MembershipService memberships;

    @Inject
    private LedgerService ledger;

    @Inject
    private AuditService audit;

    /** Newest first. */
    public List<CycleView> list(UUID groupId) {
        StokvelGroup group = groups.find(groupId);
        return cycles(group).stream().map(ledger::view).toList();
    }

    public List<ContributionCycle> cycles(StokvelGroup group) {
        return em.createQuery("SELECT c FROM ContributionCycle c WHERE c.group = :group ORDER BY c.cycleNumber DESC", ContributionCycle.class)
                .setParameter("group", group)
                .getResultList();
    }

    public Optional<ContributionCycle> openCycle(StokvelGroup group) {
        return em.createQuery("SELECT c FROM ContributionCycle c WHERE c.group = :group AND c.status = :open", ContributionCycle.class)
                .setParameter("group", group)
                .setParameter("open", CycleStatus.OPEN)
                .getResultStream()
                .findFirst();
    }

    /** The open cycle, or else the most recent one. */
    public Optional<ContributionCycle> currentCycle(StokvelGroup group) {
        return openCycle(group).or(() -> cycles(group).stream().findFirst());
    }

    public ContributionCycle find(StokvelGroup group, UUID cycleId) {
        ContributionCycle cycle = em.find(ContributionCycle.class, cycleId);
        if (cycle == null || !cycle.getGroup().getId().equals(group.getId())) {
            throw new ResourceNotFoundException("Cycle " + cycleId + " not found in " + group.getName());
        }
        return cycle;
    }

    /**
     * Opens the next cycle. Its due date follows on from the previous one (or is the group's
     * start date for the first); amountDue is copied from the group's current contribution amount.
     */
    public ContributionCycle open(UUID groupId, LocalDate dueDate) {
        StokvelGroup group = groups.find(groupId);
        access.requireRole(group, MembershipRole.TREASURER);
        groups.requireActive(group);
        openCycle(group).ifPresent(c -> {
            throw new BusinessRuleException("Cycle " + c.getCycleNumber() + " is still open. Close it first");
        });
        Optional<ContributionCycle> previous = cycles(group).stream().findFirst();
        LocalDate due = dueDate != null ? dueDate
                : previous.map(p -> group.getFrequency().next(p.getDueDate())).orElse(group.getStartDate());
        if (previous.isPresent() && !due.isAfter(previous.get().getDueDate())) {
            throw new BusinessRuleException("The due date must be after the previous cycle's (" + previous.get().getDueDate() + ")");
        }

        ContributionCycle cycle = new ContributionCycle();
        cycle.setGroup(group);
        cycle.setCycleNumber(previous.map(p -> p.getCycleNumber() + 1).orElse(1));
        cycle.setDueDate(due);
        cycle.setAmountDue(group.getContributionAmount());
        cycle.setStatus(CycleStatus.OPEN);
        cycle.setOpenedAt(LocalDateTime.now());
        em.persist(cycle);
        audit.record(group, AuditAction.CYCLE_OPENED, "ContributionCycle", cycle.getId(), Map.of(
                "cycleNumber", cycle.getCycleNumber(), "dueDate", due.toString(), "amountDue", cycle.getAmountDue()));
        return cycle;
    }

    /** No new contributions after closing; pending ones can still be verified. */
    public ContributionCycle close(UUID groupId, UUID cycleId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRole(group, MembershipRole.TREASURER);
        ContributionCycle cycle = find(group, cycleId);
        if (cycle.getStatus() != CycleStatus.OPEN) {
            throw new BusinessRuleException("Cycle " + cycle.getCycleNumber() + " is already " + cycle.getStatus().name().toLowerCase());
        }
        cycle.setStatus(CycleStatus.CLOSED);
        cycle.setClosedAt(LocalDateTime.now());
        audit.record(group, AuditAction.CYCLE_CLOSED, "ContributionCycle", cycle.getId(), Map.of(
                "cycleNumber", cycle.getCycleNumber(), "completionPercent", ledger.completionPercent(cycle),
                "verifiedTotal", ledger.verifiedTotal(cycle)));
        return cycle;
    }

    /** Final sign-off: every contribution in the cycle has been verified or rejected. */
    public ContributionCycle reconcile(UUID groupId, UUID cycleId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRole(group, MembershipRole.TREASURER);
        ContributionCycle cycle = find(group, cycleId);
        if (cycle.getStatus() != CycleStatus.CLOSED) {
            throw new BusinessRuleException("Close cycle " + cycle.getCycleNumber() + " before reconciling it");
        }
        long unresolved = ledger.unverifiedCount(cycle);
        if (unresolved > 0) {
            throw new BusinessRuleException(unresolved + " contribution(s) in cycle " + cycle.getCycleNumber()
                    + " still need to be verified or rejected");
        }
        cycle.setStatus(CycleStatus.RECONCILED);
        audit.record(group, AuditAction.CYCLE_RECONCILED, "ContributionCycle", cycle.getId(), Map.of(
                "cycleNumber", cycle.getCycleNumber(), "verifiedTotal", ledger.verifiedTotal(cycle)));
        return cycle;
    }

    /** The treasurer's grid: every expected payer and where their payment stands. */
    public CycleGrid grid(UUID groupId, UUID cycleId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        ContributionCycle cycle = find(group, cycleId);
        Map<UUID, BigDecimal> verified = ledger.verifiedByMember(cycle);
        Map<UUID, BigDecimal> waiting = ledger.unresolvedByMember(cycle);

        List<CycleGrid.Row> rows = memberships.list(groupId, false).stream()
                .filter(ms -> !ms.getJoinedDate().isAfter(cycle.getDueDate()))
                .map(ms -> row(ms, cycle.getAmountDue(), verified, waiting))
                .toList();
        return new CycleGrid(ledger.view(cycle), rows);
    }

    private static CycleGrid.Row row(Membership ms, BigDecimal due, Map<UUID, BigDecimal> verified,
                                     Map<UUID, BigDecimal> waiting) {
        UUID id = ms.getMember().getId();
        BigDecimal paid = verified.getOrDefault(id, Money.ZERO);
        BigDecimal pending = waiting.getOrDefault(id, Money.ZERO);
        String status;
        if (paid.compareTo(due) >= 0) {
            status = "PAID";
        } else if (pending.signum() > 0) {
            status = "AWAITING_VERIFICATION";
        } else if (paid.signum() > 0) {
            status = "PARTIAL";
        } else {
            status = "OUTSTANDING";
        }
        return new CycleGrid.Row(id, ms.getMember().getFullName(), ms.getPayoutPosition(), due, paid, pending, status);
    }
}
