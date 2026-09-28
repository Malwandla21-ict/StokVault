package com.stokvault.service;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.PhoneNumbers;
import com.stokvault.domain.SaIdNumber;
import com.stokvault.dto.MemberRegistration;
import com.stokvault.dto.MemberUpdate;
import com.stokvault.entity.Member;
import com.stokvault.entity.Membership;
import com.stokvault.exception.AccessDeniedException;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.InvalidRequestException;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.security.AccessControl;
import com.stokvault.security.FieldCrypto;
import com.stokvault.security.PasswordHasher;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registering people and managing their details.
 */
// @Stateless: a pooled EJB; every public method runs in a container-managed transaction.
// @RolesAllowed: the container refuses callers without one of these roles (EJBAccessException -> 403).
@Stateless
@RolesAllowed(Roles.MEMBER)
public class MemberService {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    @Inject
    private PasswordHasher hasher;

    /**
     * Registers a person (Coop Office admins and group officers can do this). They get no
     * password: they sign in the first time with an SMS code and then choose one.
     */
    @RolesAllowed({Roles.ADMIN, Roles.TREASURER, Roles.COMMITTEE})
    public Member register(MemberRegistration registration) {
        String nationalId = registration.nationalId().trim();
        if (!SaIdNumber.isValid(nationalId)) {
            throw new InvalidRequestException(SaIdNumber.mask(nationalId) + " is not a valid South African ID number");
        }
        String phone = requirePhone(registration.phoneNumber());
        String idHash = FieldCrypto.get().lookupHash(nationalId);

        if (findByPhone(phone) != null) {
            throw new BusinessRuleException("A member with phone number " + PhoneNumbers.display(phone) + " is already registered");
        }
        if (!em.createQuery("SELECT m FROM Member m WHERE m.nationalIdHash = :hash", Member.class)
                .setParameter("hash", idHash).getResultList().isEmpty()) {
            throw new BusinessRuleException("A member with this ID number is already registered");
        }

        Member member = new Member();
        member.setFullName(registration.fullName().trim());
        member.setNationalId(nationalId);
        member.setNationalIdHash(idHash);
        member.setPhoneNumber(phone);
        member.setEmail(normaliseEmail(registration.email()));
        member.setPreferredChannel(registration.preferredChannel() == null
                ? NotificationChannel.SMS : registration.preferredChannel());
        member.setPasswordHash(hasher.unusableHash());
        member.setPasswordSet(false);
        member.setPopiaConsentAt(LocalDateTime.now());
        em.persist(member);
        return member;
    }

    /**
     * Admins can search everyone by name or phone. Group officers can only look a person up by
     * their exact phone number or ID number (to add them to their group), so they can't browse
     * other stokvels' members.
     */
    @RolesAllowed({Roles.ADMIN, Roles.TREASURER, Roles.COMMITTEE})
    public List<Member> search(String query) {
        String q = query == null ? "" : query.trim();
        if (access.isAdmin()) {
            if (q.isEmpty()) {
                return em.createQuery("SELECT m FROM Member m ORDER BY m.fullName", Member.class)
                        .setMaxResults(200).getResultList();
            }
            String phone = PhoneNumbers.normalise(q);
            return em.createQuery("""
                            SELECT m FROM Member m
                            WHERE LOWER(m.fullName) LIKE :pattern OR m.phoneNumber = :phone
                            ORDER BY m.fullName""", Member.class)
                    .setParameter("pattern", "%" + q.toLowerCase(Locale.ROOT) + "%")
                    .setParameter("phone", phone == null ? "" : phone)
                    .setMaxResults(200)
                    .getResultList();
        }
        String phone = PhoneNumbers.normalise(q);
        if (phone != null) {
            Member byPhone = findByPhone(phone);
            return byPhone == null ? List.of() : List.of(byPhone);
        }
        if (SaIdNumber.isValid(q)) {
            return em.createQuery("SELECT m FROM Member m WHERE m.nationalIdHash = :hash", Member.class)
                    .setParameter("hash", FieldCrypto.get().lookupHash(q))
                    .getResultList();
        }
        throw new InvalidRequestException("Search by the person's exact phone number or ID number");
    }

    /** Yourself, anyone if you're an admin, or members of groups where you're an officer. */
    public Member find(UUID id) {
        Member member = em.find(Member.class, id);
        if (member == null || !canSee(member)) {
            throw new ResourceNotFoundException("Member " + id + " not found");
        }
        return member;
    }

    public Member me() {
        return access.currentMember();
    }

    /** Members update their own contact details; admins can update anyone's. */
    public Member update(UUID id, MemberUpdate update) {
        Member member = find(id);
        if (!access.isAdmin() && !member.getId().equals(access.currentMember().getId())) {
            throw new AccessDeniedException("You can only change your own details");
        }
        String phone = requirePhone(update.phoneNumber());
        Member samePhone = findByPhone(phone);
        if (samePhone != null && !samePhone.getId().equals(member.getId())) {
            throw new BusinessRuleException("Phone number " + PhoneNumbers.display(phone) + " belongs to another member");
        }
        member.setFullName(update.fullName().trim());
        member.setPhoneNumber(phone);
        member.setEmail(normaliseEmail(update.email()));
        if (update.preferredChannel() != null) {
            member.setPreferredChannel(update.preferredChannel());
        }
        return member;
    }

    /** The groups a member belongs (or belonged) to. */
    public List<Membership> memberships(UUID memberId) {
        Member member = find(memberId);
        return em.createQuery("SELECT ms FROM Membership ms WHERE ms.member = :member ORDER BY ms.group.name", Membership.class)
                .setParameter("member", member)
                .getResultList();
    }

    private boolean canSee(Member member) {
        if (access.isAdmin()) {
            return true;
        }
        UUID me = access.currentMemberId().orElse(null);
        if (member.getId().equals(me)) {
            return true;
        }
        // Officers see the members of their own groups
        return em.createQuery("""
                        SELECT COUNT(mine) FROM Membership mine, Membership theirs
                        WHERE mine.group = theirs.group AND mine.member.id = :me AND theirs.member = :member
                          AND mine.status = :active AND mine.role IN :officerRoles""", Long.class)
                .setParameter("me", me)
                .setParameter("member", member)
                .setParameter("active", MembershipStatus.ACTIVE)
                .setParameter("officerRoles", List.of(MembershipRole.TREASURER, MembershipRole.COMMITTEE))
                .getSingleResult() > 0;
    }

    private Member findByPhone(String normalisedPhone) {
        return em.createQuery("SELECT m FROM Member m WHERE m.phoneNumber = :phone", Member.class)
                .setParameter("phone", normalisedPhone)
                .getResultStream().findFirst().orElse(null);
    }

    private static String requirePhone(String input) {
        String phone = PhoneNumbers.normalise(input);
        if (phone == null) {
            throw new InvalidRequestException("'" + input + "' is not a valid South African phone number");
        }
        return phone;
    }

    private static String normaliseEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
