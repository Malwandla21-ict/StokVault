package com.stokvault.service;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.AuditAction;
import com.stokvault.domain.CycleStatus;
import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.MembershipRequest;
import com.stokvault.dto.MembershipUpdate;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.AccessDeniedException;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.security.AccessControl;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who belongs to which group, their roles and the payout order (SDD 4.1 "Manage Membership").
 */
@Stateless
@RolesAllowed(Roles.MEMBER)
public class MembershipService {

    private static final List<PayoutStatus> OPEN_PAYOUTS = List.of(PayoutStatus.SCHEDULED, PayoutStatus.PENDING_APPROVAL, PayoutStatus.CONFIRMED);

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private GroupService groups;

    @Inject
    private MemberService members;

    @Inject
    private AuditService audit;

    /** In payout order. Plain members see the member list too (they need to know who's in). */
    public List<Membership> list(UUID groupId, boolean includeInactive) {
        StokvelGroup group = groups.find(groupId);
        return em.createQuery("SELECT ms FROM Membership ms WHERE ms.group = :group ORDER BY ms.status, ms.payoutPosition",
                        Membership.class)
                .setParameter("group", group)
                .getResultList().stream()
                .filter(ms -> includeInactive || ms.isActive())
                .toList();
    }

    public Membership findActive(StokvelGroup group, UUID memberId) {
        return findAny(group, memberId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("That person is not an active member of " + group.getName()));
    }

    /**
     * Adds a registered member to the group. The treasurer (or an admin) does this; once a
     * contribution cycle is open, a committee member must do it instead (SDD 4.1: "rejects
     * membership additions once a contribution cycle is active without Committee approval").
     */
    public Membership add(UUID groupId, MembershipRequest request) {
        StokvelGroup group = groups.find(groupId);
        if (group.getStatus() == GroupStatus.CLOSED) {
            throw new BusinessRuleException(group.getName() + " is closed");
        }
        if (hasOpenCycle(group)) {
            if (!access.isAdmin() && !access.hasRole(group, MembershipRole.COMMITTEE)) {
                throw new AccessDeniedException("A cycle is open, so adding members needs a committee member's approval: "
                        + "ask a committee member to add them");
            }
        } else {
            access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        }

        Member member = em.find(Member.class, request.memberId());
        if (member == null) {
            throw new ResourceNotFoundException("Member " + request.memberId() + " not found");
        }
        Optional<Membership> existing = findAny(group, member.getId());
        if (existing.isPresent() && existing.get().isActive()) {
            throw new BusinessRuleException(member.getFullName() + " is already a member of " + group.getName());
        }
        MembershipRole role = request.role() == null ? MembershipRole.MEMBER : request.role();
        requireRoleAvailable(group, role, null);

        // Someone who left and rejoins gets their old row back, at the end of the payout order
        Membership membership = existing.orElseGet(Membership::new);
        membership.setGroup(group);
        membership.setMember(member);
        membership.setRole(role);
        membership.setPayoutPosition(nextPosition(group));
        membership.setJoinedDate(request.joinedDate() == null ? LocalDate.now() : request.joinedDate());
        membership.setLeftDate(null);
        membership.setStatus(MembershipStatus.ACTIVE);
        if (existing.isEmpty()) {
            em.persist(membership);
        }
        audit.record(group, AuditAction.MEMBER_ADDED, "Membership", membership.getId(), Map.of(
                "member", member.getFullName(), "memberId", member.getId().toString(), "role", role,
                "payoutPosition", membership.getPayoutPosition(), "joinedDate", membership.getJoinedDate().toString()));
        return membership;
    }

    /**
     * Role changes are made by the committee (they run the group's elections) or an admin;
     * payout positions can also be arranged by the treasurer.
     */
    public Membership update(UUID groupId, UUID memberId, MembershipUpdate update) {
        StokvelGroup group = groups.find(groupId);
        Membership membership = findActive(group, memberId);
        Map<String, Object> changes = new LinkedHashMap<>();

        if (update.role() != null && update.role() != membership.getRole()) {
            access.requireRoleOrAdmin(group, MembershipRole.COMMITTEE);
            if (membership.getRole() == MembershipRole.TREASURER) {
                throw new BusinessRuleException("Appoint a new treasurer first; that moves "
                        + membership.getMember().getFullName() + " out of the role");
            }
            if (update.role() == MembershipRole.TREASURER) {
                // Handing over: the current treasurer becomes a committee member
                activeWithRole(group, MembershipRole.TREASURER).ifPresent(current -> {
                    current.setRole(MembershipRole.COMMITTEE);
                    changes.put("previousTreasurer", current.getMember().getFullName() + " -> COMMITTEE");
                });
            }
            changes.put("role", membership.getRole() + " -> " + update.role());
            membership.setRole(update.role());
        }

        Integer position = update.payoutPosition();
        if (position != null && position != membership.getPayoutPosition()) {
            access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
            int oldPosition = membership.getPayoutPosition();
            em.createQuery("""
                            SELECT ms FROM Membership ms
                            WHERE ms.group = :group AND ms.payoutPosition = :position AND ms.status = :active""", Membership.class)
                    .setParameter("group", group)
                    .setParameter("position", position)
                    .setParameter("active", MembershipStatus.ACTIVE)
                    .getResultList()
                    .forEach(other -> {
                        other.setPayoutPosition(oldPosition);
                        changes.put("swappedWith", other.getMember().getFullName());
                    });
            membership.setPayoutPosition(position);
            changes.put("payoutPosition", oldPosition + " -> " + position);
        }

        if (!changes.isEmpty()) {
            changes.put("member", membership.getMember().getFullName());
            audit.record(group, AuditAction.MEMBER_UPDATED, "Membership", membership.getId(), changes);
        }
        return membership;
    }

    /** The member leaves: INACTIVE, history kept. */
    public Membership remove(UUID groupId, UUID memberId) {
        StokvelGroup group = groups.find(groupId);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        Membership membership = findActive(group, memberId);
        if (membership.getRole() == MembershipRole.TREASURER) {
            throw new BusinessRuleException("Appoint a new treasurer before removing " + membership.getMember().getFullName());
        }
        long openPayouts = em.createQuery("SELECT COUNT(p) FROM Payout p WHERE p.group = :group AND p.member.id = :memberId AND p.status IN :open", Long.class)
                .setParameter("group", group)
                .setParameter("memberId", memberId)
                .setParameter("open", OPEN_PAYOUTS)
                .getSingleResult();
        if (openPayouts > 0) {
            throw new BusinessRuleException(membership.getMember().getFullName()
                    + " has a payout in progress. Pay or cancel it before they leave");
        }
        membership.setStatus(MembershipStatus.INACTIVE);
        membership.setLeftDate(LocalDate.now());
        audit.record(group, AuditAction.MEMBER_REMOVED, "Membership", membership.getId(),
                Map.of("member", membership.getMember().getFullName(), "leftDate", LocalDate.now().toString()));
        return membership;
    }

    private Optional<Membership> findAny(StokvelGroup group, UUID memberId) {
        return em.createQuery("SELECT ms FROM Membership ms WHERE ms.group = :group AND ms.member.id = :memberId", Membership.class)
                .setParameter("group", group)
                .setParameter("memberId", memberId)
                .getResultStream()
                .findFirst();
    }

    private Optional<Membership> activeWithRole(StokvelGroup group, MembershipRole role) {
        return em.createQuery("SELECT ms FROM Membership ms WHERE ms.group = :group AND ms.role = :role AND ms.status = :active", Membership.class)
                .setParameter("group", group)
                .setParameter("role", role)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getResultStream()
                .findFirst();
    }

    /** One active treasurer per group (SDD 3.2). */
    private void requireRoleAvailable(StokvelGroup group, MembershipRole role, Membership except) {
        if (role != MembershipRole.TREASURER) {
            return;
        }
        activeWithRole(group, role)
                .filter(holder -> !holder.equals(except))
                .ifPresent(holder -> {
                    throw new BusinessRuleException(group.getName() + " already has a treasurer: "
                            + holder.getMember().getFullName() + ". Make the new person treasurer via a role change instead");
                });
    }

    private boolean hasOpenCycle(StokvelGroup group) {
        return em.createQuery("SELECT COUNT(c) FROM ContributionCycle c WHERE c.group = :group AND c.status = :open", Long.class)
                .setParameter("group", group)
                .setParameter("open", CycleStatus.OPEN)
                .getSingleResult() > 0;
    }

    private int nextPosition(StokvelGroup group) {
        Integer max = em.createQuery("SELECT MAX(ms.payoutPosition) FROM Membership ms WHERE ms.group = :group AND ms.status = :active", Integer.class)
                .setParameter("group", group)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
