package com.stokvault.security;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.exception.AccessDeniedException;
import com.stokvault.exception.ResourceNotFoundException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.security.enterprise.SecurityContext;

import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-group authorisation (SDD 7.2): roles are scoped to a group, so "is the caller TREASURER?"
 * always means "of this group". Every service checks access through here, and every financial
 * query is scoped by group, which is what keeps one stokvel's data invisible to another (tenant
 * isolation, SDD 7.5).
 */
@Stateless
public class AccessControl {

    // Jakarta Security's view of the logged-in caller; works in EJBs as well as web components
    @Inject
    private SecurityContext securityContext;

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    /** The logged-in member's id, or empty for anonymous/system calls (timers, batch jobs). */
    public Optional<UUID> currentMemberId() {
        Principal principal = securityContext.getCallerPrincipal();
        if (principal == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // e.g. the container's "ANONYMOUS" principal
        }
    }

    public Member currentMember() {
        UUID id = currentMemberId().orElseThrow(() -> new AccessDeniedException("Please log in"));
        Member member = em.find(Member.class, id);
        if (member == null) {
            throw new AccessDeniedException("Your account no longer exists");
        }
        return member;
    }

    /** The logged-in member, or null when the system itself is acting. */
    public Member currentMemberOrSystem() {
        return currentMemberId().map(id -> em.find(Member.class, id)).orElse(null);
    }

    public boolean isAdmin() {
        return securityContext.isCallerInRole(Roles.ADMIN);
    }

    /** The caller's active membership of the group, if any. */
    public Optional<Membership> activeMembership(StokvelGroup group) {
        return currentMemberId().flatMap(memberId -> em.createQuery("""
                        SELECT ms FROM Membership ms
                        WHERE ms.group = :group AND ms.member.id = :memberId AND ms.status = :active""", Membership.class)
                .setParameter("group", group)
                .setParameter("memberId", memberId)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getResultStream()
                .findFirst());
    }

    public Optional<MembershipRole> roleIn(StokvelGroup group) {
        return activeMembership(group).map(Membership::getRole);
    }

    public boolean hasRole(StokvelGroup group, MembershipRole... roles) {
        return roleIn(group).map(role -> Arrays.asList(roles).contains(role)).orElse(false);
    }

    /**
     * Admins and anyone who is or was a member may view a group. Everyone else gets "not found",
     * so they can't even learn that the group exists.
     */
    public void requireCanView(StokvelGroup group) {
        if (isAdmin()) {
            return;
        }
        boolean everMember = currentMemberId().map(memberId -> em.createQuery(
                        "SELECT COUNT(ms) FROM Membership ms WHERE ms.group = :group AND ms.member.id = :memberId", Long.class)
                .setParameter("group", group)
                .setParameter("memberId", memberId)
                .getSingleResult() > 0).orElse(false);
        if (!everMember) {
            throw new ResourceNotFoundException("Group " + group.getId() + " not found");
        }
    }

    /** The caller must hold one of the roles in this group (admins are NOT exempt). */
    public Membership requireRole(StokvelGroup group, MembershipRole... roles) {
        requireCanView(group);
        return activeMembership(group)
                .filter(ms -> Arrays.asList(roles).contains(ms.getRole()))
                .orElseThrow(() -> new AccessDeniedException("Only the group's " + describe(roles) + " can do that"));
    }

    /** Like requireRole, but Coop Office administrators may also do it. */
    public void requireRoleOrAdmin(StokvelGroup group, MembershipRole... roles) {
        if (!isAdmin()) {
            requireRole(group, roles);
        }
    }

    private static String describe(MembershipRole... roles) {
        return Arrays.stream(roles).map(r -> r.name().toLowerCase()).collect(Collectors.joining(" or "));
    }
}
