package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.AuditAction;
import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.Money;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.NotificationType;
import com.stokvault.domain.PaymentMethod;
import com.stokvault.domain.VerificationStatus;
import com.stokvault.dto.ContributionRequest;
import com.stokvault.dto.VerificationDecision;
import com.stokvault.entity.Contribution;
import com.stokvault.entity.ContributionCycle;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.AccessDeniedException;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.InvalidRequestException;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.notification.NotificationRequest;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recording and verifying contributions (SDD 4.2 and the data flow in 3.4).
 *
 * Recording:
 *  - a member reporting their own payment -> PENDING (the treasurer confirms it later),
 *  - the treasurer recording a payment -> VERIFIED, unless something looks wrong, in which case
 *    it's flagged PENDING_REVIEW instead of being rejected, so the record isn't lost:
 *      * the amount differs from what the member still owes for the cycle, or
 *      * the payment reference was already used for another contribution in the group.
 *  - submitting the same (member, cycle, reference) again returns the existing record instead of
 *    creating a duplicate (idempotency).
 * Verifying: the treasurer or a committee member, but never for their own contribution.
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class ContributionService {

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

    /** created = false when an identical contribution already existed (nothing new was recorded). */
    public record Recorded(Contribution contribution, boolean created) {
    }

    public Recorded record(UUID groupId, ContributionRequest request) {
        StokvelGroup group = groups.find(groupId);
        groups.requireActive(group);
        Member caller = access.currentMember();
        Membership callerMembership = access.activeMembership(group)
                .orElseThrow(() -> new AccessDeniedException("Only members of " + group.getName() + " can record contributions"));

        UUID payerId = request.memberId() == null ? caller.getId() : request.memberId();
        boolean selfReported = payerId.equals(caller.getId());
        if (!selfReported && callerMembership.getRole() != MembershipRole.TREASURER) {
            throw new AccessDeniedException("Only the treasurer can record contributions for other members");
        }
        Membership payer = memberships.findActive(group, payerId);

        ContributionCycle cycle = request.cycleId() != null
                ? cycles.find(group, request.cycleId())
                : cycles.openCycle(group).orElseThrow(() -> new BusinessRuleException(
                        "No contribution cycle is open in " + group.getName() + ". The treasurer must open one first"));
        if (cycle.getStatus() != CycleStatus.OPEN) {
            throw new BusinessRuleException("Cycle " + cycle.getCycleNumber() + " is " + cycle.getStatus().name().toLowerCase()
                    + "; contributions can only be recorded against the open cycle");
        }
        String reference = request.paymentReference().trim();

        // Idempotency: the same payment submitted twice is the same contribution
        List<Contribution> same = em.createQuery("""
                        SELECT c FROM Contribution c
                        WHERE c.cycle = :cycle AND c.member = :member AND LOWER(c.paymentReference) = LOWER(:reference)""", Contribution.class)
                .setParameter("cycle", cycle)
                .setParameter("member", payer.getMember())
                .setParameter("reference", reference)
                .getResultList();
        if (!same.isEmpty()) {
            return new Recorded(same.get(0), false);
        }

        LocalDate date = request.contributionDate() == null ? LocalDate.now() : request.contributionDate();
        List<String> flags = checks(group, cycle, payer.getMember(), request.amount(), reference);

        Contribution contribution = new Contribution();
        contribution.setGroup(group);
        contribution.setCycle(cycle);
        contribution.setMember(payer.getMember());
        contribution.setAmount(request.amount());
        contribution.setPaymentReference(reference);
        contribution.setPaymentMethod(request.paymentMethod() == null ? PaymentMethod.EFT : request.paymentMethod());
        contribution.setContributionDate(date);
        contribution.setRecordedBy(caller);
        if (selfReported) {
            // Covers the treasurer paying in their own money too: someone else must verify it
            contribution.setVerificationStatus(VerificationStatus.PENDING);
            contribution.setReviewNote(flags.isEmpty() ? null : String.join("; ", flags));
        } else if (!flags.isEmpty()) {
            contribution.setVerificationStatus(VerificationStatus.PENDING_REVIEW);
            contribution.setReviewNote(String.join("; ", flags));
        } else {
            contribution.setVerificationStatus(VerificationStatus.VERIFIED);
            contribution.setVerifiedBy(caller);
            contribution.setVerifiedAt(LocalDateTime.now());
        }
        em.persist(contribution);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("member", payer.getMember().getFullName());
        details.put("cycleNumber", cycle.getCycleNumber());
        details.put("amount", contribution.getAmount());
        details.put("paymentReference", reference);
        details.put("paymentMethod", contribution.getPaymentMethod());
        details.put("status", contribution.getVerificationStatus());
        if (contribution.getReviewNote() != null) {
            details.put("flags", contribution.getReviewNote());
        }
        audit.record(group, AuditAction.CONTRIBUTION_RECORDED, "Contribution", contribution.getId(), details);

        String status = switch (contribution.getVerificationStatus()) {
            case VERIFIED -> "It has been verified.";
            case PENDING_REVIEW -> "The treasurer will review it: " + contribution.getReviewNote() + ".";
            default -> "It is waiting for verification.";
        };
        notifications.fire(NotificationRequest.of(payer.getMember().getId(), group.getId(), NotificationType.CONTRIBUTION_RECORDED,
                "Contribution recorded", "StokVault: your " + Money.format(contribution.getAmount()) + " contribution to "
                        + group.getName() + " (cycle " + cycle.getCycleNumber() + ", ref " + reference + ") was recorded. " + status));
        return new Recorded(contribution, true);
    }

    public Contribution verify(UUID groupId, UUID contributionId, VerificationDecision decision) {
        StokvelGroup group = groups.find(groupId);
        Membership verifier = access.requireRole(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        Contribution contribution = find(group, contributionId);
        if (decision.decision() != VerificationStatus.VERIFIED && decision.decision() != VerificationStatus.REJECTED) {
            throw new InvalidRequestException("The decision must be VERIFIED or REJECTED");
        }
        if (!contribution.getVerificationStatus().isUnresolved()) {
            throw new BusinessRuleException("This contribution is already " + contribution.getVerificationStatus().name().toLowerCase());
        }
        if (contribution.getCycle().getStatus() == CycleStatus.RECONCILED) {
            throw new BusinessRuleException("Cycle " + contribution.getCycle().getCycleNumber() + " is reconciled and can't change");
        }
        if (contribution.getMember().getId().equals(verifier.getMember().getId())) {
            throw new AccessDeniedException("You can't verify your own contribution; another officer must do it");
        }
        if (decision.decision() == VerificationStatus.REJECTED && (decision.note() == null || decision.note().isBlank())) {
            throw new InvalidRequestException("Give a reason when rejecting a contribution");
        }

        VerificationStatus before = contribution.getVerificationStatus();
        contribution.setVerificationStatus(decision.decision());
        contribution.setVerifiedBy(verifier.getMember());
        contribution.setVerifiedAt(LocalDateTime.now());
        if (decision.note() != null && !decision.note().isBlank()) {
            contribution.setReviewNote(decision.note().trim());
        }
        boolean verified = decision.decision() == VerificationStatus.VERIFIED;
        audit.record(group, verified ? AuditAction.CONTRIBUTION_VERIFIED : AuditAction.CONTRIBUTION_REJECTED,
                "Contribution", contribution.getId(), Map.of(
                        "member", contribution.getMember().getFullName(), "amount", contribution.getAmount(),
                        "from", before, "to", decision.decision(),
                        "note", decision.note() == null ? "" : decision.note().trim()));
        notifications.fire(NotificationRequest.of(contribution.getMember().getId(), group.getId(),
                verified ? NotificationType.CONTRIBUTION_VERIFIED : NotificationType.CONTRIBUTION_REJECTED,
                verified ? "Contribution verified" : "Contribution rejected",
                "StokVault: your " + Money.format(contribution.getAmount()) + " contribution to " + group.getName()
                        + " (ref " + contribution.getPaymentReference() + ") was " + (verified ? "verified." : "rejected: " + decision.note().trim())));
        return contribution;
    }

    /**
     * Officers and admins see every contribution (optionally filtered); a plain member only
     * ever sees their own.
     */
    public List<Contribution> list(UUID groupId, UUID cycleId, UUID memberId, VerificationStatus status) {
        StokvelGroup group = groups.find(groupId);
        boolean officer = access.isAdmin() || access.hasRole(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        UUID onlyMember = officer ? memberId : access.currentMember().getId();

        StringBuilder jpql = new StringBuilder("SELECT c FROM Contribution c WHERE c.group = :group");
        if (cycleId != null) {
            jpql.append(" AND c.cycle.id = :cycleId");
        }
        if (onlyMember != null) {
            jpql.append(" AND c.member.id = :memberId");
        }
        if (status != null) {
            jpql.append(" AND c.verificationStatus = :status");
        }
        jpql.append(" ORDER BY c.cycle.cycleNumber DESC, c.createdAt DESC");
        TypedQuery<Contribution> query = em.createQuery(jpql.toString(), Contribution.class).setParameter("group", group);
        if (cycleId != null) {
            query.setParameter("cycleId", cycleId);
        }
        if (onlyMember != null) {
            query.setParameter("memberId", onlyMember);
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getResultList();
    }

    public Contribution find(StokvelGroup group, UUID contributionId) {
        Contribution c = em.find(Contribution.class, contributionId);
        if (c == null || !c.getGroup().getId().equals(group.getId())) {
            throw new ResourceNotFoundException("Contribution " + contributionId + " not found in " + group.getName());
        }
        return c;
    }

    /** Reasons to flag a new contribution for review; empty when it looks right. */
    private List<String> checks(StokvelGroup group, ContributionCycle cycle, Member payer, BigDecimal amount, String reference) {
        List<String> flags = new ArrayList<>();
        BigDecimal alreadyPaid = ledger.verifiedByMember(cycle).getOrDefault(payer.getId(), Money.ZERO)
                .add(ledger.unresolvedByMember(cycle).getOrDefault(payer.getId(), Money.ZERO));
        BigDecimal stillDue = cycle.getAmountDue().subtract(alreadyPaid).max(Money.ZERO);
        if (Money.of(amount).compareTo(stillDue) != 0) {
            flags.add(stillDue.signum() == 0
                    ? "the cycle is already fully paid for this member"
                    : "amount " + Money.format(amount) + " differs from the " + Money.format(stillDue) + " due");
        }
        long reused = em.createQuery("""
                        SELECT COUNT(c) FROM Contribution c
                        WHERE c.group = :group AND LOWER(c.paymentReference) = LOWER(:reference)
                          AND c.verificationStatus <> :rejected""", Long.class)
                .setParameter("group", group)
                .setParameter("reference", reference)
                .setParameter("rejected", VerificationStatus.REJECTED)
                .getSingleResult();
        if (reused > 0) {
            flags.add("payment reference " + reference + " was already used for another contribution");
        }
        return flags;
    }
}
