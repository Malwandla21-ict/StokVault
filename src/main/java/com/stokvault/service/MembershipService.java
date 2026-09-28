package com.stokvault.service;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.MembershipRequest;
import com.stokvault.dto.MembershipUpdateRequest;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.entity.Stokvel;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.ResourceNotFoundException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Who belongs to which stokvel, their roles, and the payout order.
 */
@Stateless
public class MembershipService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    // One EJB can use another through @Inject. The call joins the caller's transaction,
    // so everything in one request commits or rolls back together.
    @Inject
    private StokvelService stokvelService;

    @Inject
    private MemberService memberService;

    public List<Membership> list(Long stokvelId, boolean includeInactive) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        String jpql = "SELECT ms FROM Membership ms WHERE ms.stokvel = :stokvel"
                + (includeInactive ? "" : " AND ms.leftOn IS NULL")
                + " ORDER BY ms.payoutPosition";
        return em.createQuery(jpql, Membership.class)
                .setParameter("stokvel", stokvel)
                .getResultList();
    }

    /** The member's current (not left) membership of the stokvel, or 404. */
    public Membership findActive(Long stokvelId, Long memberId) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        return findAny(stokvel.getId(), memberId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member " + memberId + " is not an active member of " + stokvel.getName()));
    }

    public Membership add(Long stokvelId, MembershipRequest request) {
        Stokvel stokvel = stokvelService.find(stokvelId);
        stokvelService.requireActive(stokvel);
        Member member = memberService.find(request.memberId());
        MembershipRole role = request.role() == null ? MembershipRole.MEMBER : request.role();

        Optional<Membership> existing = findAny(stokvelId, member.getId());
        if (existing.isPresent() && existing.get().isActive()) {
            throw new BusinessRuleException(member.getName() + " is already a member of " + stokvel.getName());
        }
        requireRoleAvailable(stokvel, role, null);

        // Someone who left and rejoins gets their old row back, at the back of the payout queue
        Membership membership = existing.orElseGet(Membership::new);
        membership.setStokvel(stokvel);
        membership.setMember(member);
        membership.setRole(role);
        membership.setPayoutPosition(nextPayoutPosition(stokvel));
        membership.setJoinedOn(request.joinedOn() == null ? LocalDate.now() : request.joinedOn());
        membership.setLeftOn(null);
        if (existing.isEmpty()) {
            em.persist(membership);
        }
        return membership;
    }

    public Membership update(Long stokvelId, Long memberId, MembershipUpdateRequest request) {
        Membership membership = findActive(stokvelId, memberId);

        if (request.role() != null && request.role() != membership.getRole()) {
            requireRoleAvailable(membership.getStokvel(), request.role(), membership);
            membership.setRole(request.role());
        }

        Integer newPosition = request.payoutPosition();
        if (newPosition != null && newPosition != membership.getPayoutPosition()) {
            // If someone else holds that position, the two members swap places
            int oldPosition = membership.getPayoutPosition();
            em.createQuery("""
                            SELECT ms FROM Membership ms
                            WHERE ms.stokvel = :stokvel AND ms.payoutPosition = :position
                              AND ms.leftOn IS NULL""", Membership.class)
                    .setParameter("stokvel", membership.getStokvel())
                    .setParameter("position", newPosition)
                    .getResultList()
                    .forEach(other -> other.setPayoutPosition(oldPosition));
            membership.setPayoutPosition(newPosition);
        }
        return membership;
    }

    /** The member leaves the stokvel. Their history stays; they just stop being active. */
    public Membership remove(Long stokvelId, Long memberId) {
        Membership membership = findActive(stokvelId, memberId);
        Long scheduled = em.createQuery(
                        "SELECT COUNT(p) FROM Payout p WHERE p.membership = :membership AND p.status = :status",
                        Long.class)
                .setParameter("membership", membership)
                .setParameter("status", PayoutStatus.SCHEDULED)
                .getSingleResult();
        if (scheduled > 0) {
            throw new BusinessRuleException(membership.getMember().getName()
                    + " has a scheduled payout. Pay or cancel it before they leave");
        }
        membership.setLeftOn(LocalDate.now());
        return membership;
    }

    private Optional<Membership> findAny(Long stokvelId, Long memberId) {
        return em.createQuery("""
                        SELECT ms FROM Membership ms
                        WHERE ms.stokvel.id = :stokvelId AND ms.member.id = :memberId""", Membership.class)
                .setParameter("stokvelId", stokvelId)
                .setParameter("memberId", memberId)
                .getResultStream()
                .findFirst();
    }

    /** Only one active chairperson, treasurer and secretary per stokvel. */
    private void requireRoleAvailable(Stokvel stokvel, MembershipRole role, Membership except) {
        if (!role.isOffice()) {
            return;
        }
        em.createQuery("""
                        SELECT ms FROM Membership ms
                        WHERE ms.stokvel = :stokvel AND ms.role = :role AND ms.leftOn IS NULL""", Membership.class)
                .setParameter("stokvel", stokvel)
                .setParameter("role", role)
                .getResultStream()
                .filter(holder -> !holder.equals(except))
                .findFirst()
                .ifPresent(holder -> {
                    throw new BusinessRuleException(stokvel.getName() + " already has a "
                            + role.name().toLowerCase() + ": " + holder.getMember().getName());
                });
    }

    private int nextPayoutPosition(Stokvel stokvel) {
        Integer max = em.createQuery(
                        "SELECT MAX(ms.payoutPosition) FROM Membership ms WHERE ms.stokvel = :stokvel AND ms.leftOn IS NULL",
                        Integer.class)
                .setParameter("stokvel", stokvel)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
