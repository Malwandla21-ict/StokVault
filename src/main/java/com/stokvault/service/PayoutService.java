package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.batch.EligibilityJobs;
import com.stokvault.batch.PayoutsScheduled;
import com.stokvault.domain.AuditAction;
import com.stokvault.domain.EligibilityCheck;
import com.stokvault.domain.GroupType;
import com.stokvault.domain.Money;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.NotificationType;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.domain.rules.PayoutContext;
import com.stokvault.domain.rules.PayoutRules;
import com.stokvault.domain.rules.PlannedPayout;
import com.stokvault.dto.PayoutRunRequest;
import com.stokvault.entity.ContributionCycle;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.entity.Payout;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.AccessDeniedException;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.notification.NotificationRequest;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payouts (SDD 4.3):
 *   run      (treasurer)  the group type's rule plans the payouts -> SCHEDULED; the eligibility
 *                         batch job then checks each one automatically
 *   override (committee)  accept a FAILED check anyway, with a reason
 *   confirm  (treasurer)  needs PASSED/OVERRIDDEN; above the approval threshold -> PENDING_APPROVAL,
 *                         otherwise -> CONFIRMED
 *   approve  (committee)  four-eyes: someone other than the treasurer who confirmed it and the
 *                         recipient -> CONFIRMED
 *   pay      (treasurer)  CONFIRMED -> PAID, only if the balance covers it (group row locked)
 *   cancel   (treasurer or committee) any open payout -> CANCELLED
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class PayoutService {

    private static final List<PayoutStatus> OPEN = List.of(PayoutStatus.SCHEDULED, PayoutStatus.PENDING_APPROVAL, PayoutStatus.CONFIRMED);

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private GroupService groups;

    @Inject
    private MembershipService memberships;

    @Inject
    private CycleService cycles;

    @Inject
    private LedgerService ledger;

    @Inject
    private AuditService audit;

    @Inject
    private Event<NotificationRequest> notifications;

    // Observed after commit to start the eligibility batch job (see EligibilityJobTrigger)
    @Inject
    private Event<PayoutsScheduled> payoutsScheduled;

    @Inject
    private EligibilityJobs eligibilityJobs;

    /** Every member can see the group's payouts: transparency is the point of the platform. */
    public List<Payout> list(UUID groupId, PayoutStatus status) {
        StokvelGroup group = groups.find(groupId);
        var query = em.createQuery("SELECT p FROM Payout p WHERE p.group = :group"
                + (status == null ? "" : " AND p.status = :status") + " ORDER BY p.createdAt DESC", Payout.class)
                .setParameter("group", group);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getResultList();
    }

    public Payout find(StokvelGroup group, UUID payoutId) {
        Payout payout = em.find(Payout.class, payoutId);
        if (payout == null || !payout.getGroup().getId().equals(group.getId())) {
            throw new ResourceNotFoundException("Payout " + payoutId + " not found in " + group.getName());
        }
        return payout;
    }

    /** Runs the automated eligibility check for this group's scheduled payouts now (asynchronously). */
    public long startEligibilityCheck(UUID groupId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        return eligibilityJobs.start(group.getId());
    }

    /** Payouts waiting for approval in the groups where the caller sits on the committee. */
    @RolesAllowed(Roles.COMMITTEE)
    public List<Payout> approvalQueue() {
        return em.createQuery("""
                        SELECT p FROM Payout p, Membership ms
                        WHERE ms.group = p.group AND ms.member.id = :me AND ms.role = :committee
                          AND ms.status = :active AND p.status = :pending
                        ORDER BY p.createdAt""", Payout.class)
                .setParameter("me", access.currentMember().getId())
                .setParameter("committee", MembershipRole.COMMITTEE)
                .setParameter("active", MembershipStatus.ACTIVE)
                .setParameter("pending", PayoutStatus.PENDING_APPROVAL)
                .getResultList();
    }

    /** Initiate Payout: plans the payouts with the group type's rule and schedules them. */
    public List<Payout> run(UUID groupId, @Valid @NotNull PayoutRunRequest request) {
        StokvelGroup group = groups.findForUpdate(groupId);
        Membership treasurer = access.requireRole(group, MembershipRole.TREASURER);
        groups.requireActive(group);

        ContributionCycle cycle = null;
        if (group.getType() == GroupType.ROTATIONAL) {
            cycle = request.cycleId() != null ? cycles.find(group, request.cycleId())
                    : cycles.currentCycle(group).orElseThrow(() -> new BusinessRuleException("There are no cycles to pay out yet"));
            long existing = em.createQuery("SELECT COUNT(p) FROM Payout p WHERE p.cycle = :cycle AND p.status <> :cancelled", Long.class)
                    .setParameter("cycle", cycle)
                    .setParameter("cancelled", PayoutStatus.CANCELLED)
                    .getSingleResult();
            if (existing > 0) {
                throw new BusinessRuleException("Cycle " + cycle.getCycleNumber() + " has already been paid out (or is being paid out)");
            }
        } else if (group.getType() != GroupType.BURIAL) {
            long open = em.createQuery("SELECT COUNT(p) FROM Payout p WHERE p.group = :group AND p.status IN :open", Long.class)
                    .setParameter("group", group).setParameter("open", OPEN).getSingleResult();
            if (open > 0) {
                throw new BusinessRuleException("Finish or cancel the " + open + " payout(s) already in progress before sharing out again");
            }
        }

        PayoutContext context = new PayoutContext(positions(group), cycle == null ? null : ledger.verifiedTotal(cycle),
                ledger.balance(group), group.getBenefitAmount(), request.beneficiaryMemberId(), request.amountToDistribute());
        List<PlannedPayout> plan;
        try {
            plan = PayoutRules.forType(group.getType()).plan(context);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(e.getMessage());
        }

        List<Payout> created = new ArrayList<>();
        for (PlannedPayout planned : plan) {
            Payout payout = new Payout();
            payout.setGroup(group);
            payout.setMember(em.find(Member.class, planned.memberId()));
            payout.setCycle(cycle);
            payout.setAmount(planned.amount());
            payout.setStatus(PayoutStatus.SCHEDULED);
            payout.setEligibilityCheck(EligibilityCheck.NOT_RUN);
            payout.setPayoutDate(request.payoutDate() != null ? request.payoutDate() : LocalDate.now());
            payout.setInitiatedBy(treasurer.getMember());
            payout.setNotes(request.notes() == null || request.notes().isBlank()
                    ? planned.note() : planned.note() + ". " + request.notes().trim());
            em.persist(payout);
            audit.record(group, AuditAction.PAYOUT_SCHEDULED, "Payout", payout.getId(), Map.of(
                    "recipient", payout.getMember().getFullName(), "amount", payout.getAmount(),
                    "rule", group.getType(), "note", payout.getNotes(),
                    "cycleNumber", cycle == null ? "none" : String.valueOf(cycle.getCycleNumber())));
            created.add(payout);
        }
        payoutsScheduled.fire(new PayoutsScheduled(group.getId()));
        return created;
    }

    public Payout confirm(UUID groupId, UUID payoutId) {
        StokvelGroup group = groups.find(groupId);
        Membership treasurer = access.requireRole(group, MembershipRole.TREASURER);
        groups.requireActive(group);
        Payout payout = find(group, payoutId);
        requireStatus(payout, PayoutStatus.SCHEDULED);
        if (!payout.getEligibilityCheck().allowsProgress()) {
            throw new BusinessRuleException(payout.getEligibilityCheck() == EligibilityCheck.NOT_RUN
                    ? "The automated eligibility check hasn't run yet for this payout. Run it, then confirm"
                    : "The eligibility check failed (" + payout.getEligibilityNotes()
                    + "). Resolve the issues, or ask the committee to override it with a reason");
        }
        payout.setConfirmedBy(treasurer.getMember());
        boolean needsApproval = payout.getAmount().compareTo(group.getApprovalThreshold()) > 0;
        if (needsApproval) {
            payout.setStatus(PayoutStatus.PENDING_APPROVAL);
            audit.record(group, AuditAction.PAYOUT_SUBMITTED_FOR_APPROVAL, "Payout", payout.getId(), Map.of(
                    "amount", payout.getAmount(), "threshold", group.getApprovalThreshold(),
                    "recipient", payout.getMember().getFullName()));
            notifyCommittee(group, payout);
        } else {
            payout.setStatus(PayoutStatus.CONFIRMED);
            audit.record(group, AuditAction.PAYOUT_CONFIRMED, "Payout", payout.getId(), Map.of(
                    "amount", payout.getAmount(), "recipient", payout.getMember().getFullName()));
        }
        return payout;
    }

    /** Four-eyes approval of a high-value payout. */
    public Payout approve(UUID groupId, UUID payoutId) {
        StokvelGroup group = groups.find(groupId);
        Membership approver = access.requireRole(group, MembershipRole.COMMITTEE);
        groups.requireActive(group);
        Payout payout = find(group, payoutId);
        requireStatus(payout, PayoutStatus.PENDING_APPROVAL);
        UUID me = approver.getMember().getId();
        if (me.equals(payout.getMember().getId())) {
            throw new AccessDeniedException("You can't approve a payout to yourself");
        }
        if (payout.getConfirmedBy() != null && me.equals(payout.getConfirmedBy().getId())) {
            throw new AccessDeniedException("A second person must approve: you confirmed this payout");
        }
        payout.setApprovedBy(approver.getMember());
        payout.setStatus(PayoutStatus.CONFIRMED);
        audit.record(group, AuditAction.PAYOUT_APPROVED, "Payout", payout.getId(), Map.of(
                "amount", payout.getAmount(), "recipient", payout.getMember().getFullName()));
        return payout;
    }

    /** A committee member accepts a failed eligibility check, with a recorded reason (SDD 4.3). */
    public Payout override(UUID groupId, UUID payoutId, String reason) {
        StokvelGroup group = groups.find(groupId);
        Membership committee = access.requireRole(group, MembershipRole.COMMITTEE);
        Payout payout = find(group, payoutId);
        requireStatus(payout, PayoutStatus.SCHEDULED);
        if (payout.getEligibilityCheck() != EligibilityCheck.FAILED) {
            throw new BusinessRuleException("Only a failed eligibility check can be overridden (this one is "
                    + payout.getEligibilityCheck() + ")");
        }
        if (committee.getMember().getId().equals(payout.getMember().getId())) {
            throw new AccessDeniedException("You can't override the check on a payout to yourself");
        }
        payout.setEligibilityCheck(EligibilityCheck.OVERRIDDEN);
        payout.setOverrideReason(reason.trim());
        payout.setOverriddenBy(committee.getMember());
        audit.record(group, AuditAction.PAYOUT_ELIGIBILITY_OVERRIDDEN, "Payout", payout.getId(), Map.of(
                "failedChecks", payout.getEligibilityNotes() == null ? "" : payout.getEligibilityNotes(),
                "reason", reason.trim(), "amount", payout.getAmount()));
        return payout;
    }

    /** Money leaves the group. The group row is locked so two payments can't spend the same balance. */
    public Payout pay(UUID groupId, UUID payoutId) {
        StokvelGroup group = groups.findForUpdate(groupId);
        access.requireRole(group, MembershipRole.TREASURER);
        groups.requireActive(group);
        Payout payout = find(group, payoutId);
        requireStatus(payout, PayoutStatus.CONFIRMED);
        BigDecimal balance = ledger.balance(group);
        if (payout.getAmount().compareTo(balance) > 0) {
            throw new BusinessRuleException("Insufficient funds: the balance is " + Money.format(balance)
                    + " but this payout is " + Money.format(payout.getAmount()));
        }
        payout.setStatus(PayoutStatus.PAID);
        payout.setPaidAt(LocalDateTime.now());
        audit.record(group, AuditAction.PAYOUT_PAID, "Payout", payout.getId(), Map.of(
                "amount", payout.getAmount(), "recipient", payout.getMember().getFullName(),
                "balanceBefore", balance, "balanceAfter", balance.subtract(payout.getAmount())));
        notifications.fire(NotificationRequest.of(payout.getMember().getId(), group.getId(), NotificationType.PAYOUT_PAID,
                "Payout made", "StokVault: " + group.getName() + " has paid you " + Money.format(payout.getAmount()) + "."));
        return payout;
    }

    public Payout cancel(UUID groupId, UUID payoutId, String reason) {
        StokvelGroup group = groups.find(groupId);
        access.requireRole(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        Payout payout = find(group, payoutId);
        if (!payout.getStatus().isOpen()) {
            throw new BusinessRuleException("This payout is already " + payout.getStatus().name().toLowerCase());
        }
        PayoutStatus before = payout.getStatus();
        payout.setStatus(PayoutStatus.CANCELLED);
        audit.record(group, AuditAction.PAYOUT_CANCELLED, "Payout", payout.getId(), Map.of(
                "from", before, "amount", payout.getAmount(), "recipient", payout.getMember().getFullName(),
                "reason", reason == null ? "" : reason.trim()));
        return payout;
    }

    /** Active members with their rotation history and verified contributions, for the payout rules. */
    public List<PayoutContext.MemberPosition> positions(StokvelGroup group) {
        Map<UUID, Long> received = em.createQuery("""
                        SELECT p.member.id, COUNT(p) FROM Payout p
                        WHERE p.group = :group AND p.status <> :cancelled GROUP BY p.member.id""", Object[].class)
                .setParameter("group", group)
                .setParameter("cancelled", PayoutStatus.CANCELLED)
                .getResultStream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).longValue()));
        Map<UUID, BigDecimal> contributed = em.createQuery("""
                        SELECT c.member.id, SUM(c.amount) FROM Contribution c
                        WHERE c.group = :group AND c.verificationStatus = :verified GROUP BY c.member.id""", Object[].class)
                .setParameter("group", group)
                .setParameter("verified", VerificationStatus.VERIFIED)
                .getResultStream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> Money.of((BigDecimal) r[1])));
        return memberships.list(group.getId(), false).stream()
                .map(ms -> new PayoutContext.MemberPosition(ms.getMember().getId(), ms.getMember().getFullName(),
                        ms.getPayoutPosition(), received.getOrDefault(ms.getMember().getId(), 0L),
                        contributed.getOrDefault(ms.getMember().getId(), Money.ZERO)))
                .toList();
    }

    private void notifyCommittee(StokvelGroup group, Payout payout) {
        em.createQuery("SELECT ms FROM Membership ms WHERE ms.group = :group AND ms.role = :committee AND ms.status = :active", Membership.class)
                .setParameter("group", group)
                .setParameter("committee", MembershipRole.COMMITTEE)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getResultStream()
                .filter(ms -> !ms.getMember().getId().equals(payout.getMember().getId()))
                .forEach(ms -> notifications.fire(NotificationRequest.of(ms.getMember().getId(), group.getId(),
                        NotificationType.PAYOUT_APPROVAL_REQUESTED, "Payout needs your approval",
                        "StokVault: a " + Money.format(payout.getAmount()) + " payout to " + payout.getMember().getFullName()
                                + " from " + group.getName() + " needs a committee member's approval.")));
    }

    private static void requireStatus(Payout payout, PayoutStatus expected) {
        if (payout.getStatus() != expected) {
            throw new BusinessRuleException("This payout is " + payout.getStatus().name().toLowerCase().replace('_', ' ')
                    + "; it must be " + expected.name().toLowerCase().replace('_', ' ') + " for this step");
        }
    }
}
