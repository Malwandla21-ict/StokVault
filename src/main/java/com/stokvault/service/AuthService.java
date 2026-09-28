package com.stokvault.service;

import com.stokvault.domain.MembershipRole;
import com.stokvault.domain.MembershipStatus;
import com.stokvault.domain.NotificationType;
import com.stokvault.domain.PhoneNumbers;
import com.stokvault.dto.AuthRequests;
import com.stokvault.entity.Member;
import com.stokvault.exception.BusinessRuleException;
import com.stokvault.exception.InvalidRequestException;
import com.stokvault.notification.NotificationRequest;
import com.stokvault.security.AccessControl;
import com.stokvault.security.PasswordHasher;
import com.stokvault.security.Roles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Checks credentials for the identity store, with the lockout and one-time-code rules from
 * SDD 4.6 / 7.1:
 *  - 5 failed attempts lock the account for 15 minutes,
 *  - login codes are 6 digits, sent by SMS, valid for 5 minutes and usable once,
 *  - at most one code per minute, and asking for a code never reveals whether a number is registered.
 */
// @PermitAll: logging in must work before anyone is logged in. Individual methods narrow it.
@Stateless
@PermitAll
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int LOCK_MINUTES = 15;
    static final int CODE_VALID_MINUTES = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A successful login: who, and their roles (become the caller's groups). */
    public record Login(UUID memberId, Set<String> roles) {
    }

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private PasswordHasher hasher;

    @Inject
    private AccessControl access;

    // Firing this event queues an SMS (see NotificationPublisher)
    @Inject
    private Event<NotificationRequest> notifications;

    public Optional<Login> checkPassword(String phoneNumber, String password) {
        Member member = findByPhone(phoneNumber);
        if (member == null || member.isLocked() || password == null) {
            return Optional.empty();
        }
        if (hasher.matches(password, member.getPasswordHash())) {
            return Optional.of(succeed(member));
        }
        fail(member);
        return Optional.empty();
    }

    public Optional<Login> checkOtp(String phoneNumber, String code) {
        Member member = findByPhone(phoneNumber);
        if (member == null || member.isLocked() || code == null) {
            return Optional.empty();
        }
        boolean valid = member.getOtpHash() != null
                && member.getOtpExpiresAt() != null
                && member.getOtpExpiresAt().isAfter(LocalDateTime.now())
                && hasher.matches(code.trim(), member.getOtpHash());
        if (valid) {
            member.setOtpHash(null); // one use only
            member.setOtpExpiresAt(null);
            return Optional.of(succeed(member));
        }
        fail(member);
        return Optional.empty();
    }

    /** Sends a login code if the number belongs to an unlocked account. Always "succeeds". */
    public void requestLoginCode(String phoneNumber) {
        Member member = findByPhone(phoneNumber);
        if (member == null || member.isLocked()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (member.getOtpExpiresAt() != null && member.getOtpExpiresAt().isAfter(now.plusMinutes(CODE_VALID_MINUTES - 1))) {
            return; // a code was sent less than a minute ago
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        member.setOtpHash(hasher.hash(code));
        member.setOtpExpiresAt(now.plusMinutes(CODE_VALID_MINUTES));
        String text = "Your StokVault login code is %s. It expires in %d minutes. Never share this code.";
        notifications.fire(new NotificationRequest(member.getId(), null, NotificationType.LOGIN_CODE,
                "Your StokVault login code", text.formatted(code, CODE_VALID_MINUTES),
                text.formatted("******", CODE_VALID_MINUTES), null));
    }

    @RolesAllowed(Roles.MEMBER)
    public void changePassword(@Valid @NotNull AuthRequests.PasswordChange change) {
        Member member = access.currentMember();
        if (member.isPasswordSet()
                && (change.currentPassword() == null || !hasher.matches(change.currentPassword(), member.getPasswordHash()))) {
            throw new BusinessRuleException("Your current password is incorrect");
        }
        String password = change.newPassword();
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new InvalidRequestException("The new password must contain at least one letter and one number");
        }
        member.setPasswordHash(hasher.hash(password));
        member.setPasswordSet(true);
    }

    /** Roles for the caller: MEMBER for everyone, plus ADMIN / TREASURER / COMMITTEE where held. */
    public Set<String> rolesFor(Member member) {
        Set<String> roles = new HashSet<>();
        roles.add(Roles.MEMBER);
        if (member.isAdmin()) {
            roles.add(Roles.ADMIN);
        }
        em.createQuery("SELECT DISTINCT ms.role FROM Membership ms WHERE ms.member = :member AND ms.status = :active",
                        MembershipRole.class)
                .setParameter("member", member)
                .setParameter("active", MembershipStatus.ACTIVE)
                .getResultStream()
                .filter(role -> role != MembershipRole.MEMBER)
                .forEach(role -> roles.add(role.name()));
        return roles;
    }

    private Login succeed(Member member) {
        member.setFailedLoginAttempts(0);
        member.setLockedUntil(null);
        return new Login(member.getId(), rolesFor(member));
    }

    private void fail(Member member) {
        int attempts = member.getFailedLoginAttempts() + 1;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            member.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            attempts = 0;
        }
        member.setFailedLoginAttempts(attempts);
    }

    private Member findByPhone(String phoneNumber) {
        String normalised = PhoneNumbers.normalise(phoneNumber);
        if (normalised == null) {
            return null;
        }
        return em.createQuery("SELECT m FROM Member m WHERE m.phoneNumber = :phone", Member.class)
                .setParameter("phone", normalised)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
