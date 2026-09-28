package com.stokvault.entity;

import com.stokvault.domain.NotificationChannel;
import com.stokvault.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A person with a StokVault account (SDD 5.2 "Member"). They log in with their phone number and
 * a password or a one-time SMS code, and join groups through Membership rows.
 */
// @Entity: JPA stores this class in the database; @Table names the table
@Entity
@Table(name = "member_accounts")
public class Member {

    // @Id + @GeneratedValue(UUID): JPA generates a random UUID primary key on persist.
    // UUIDs (SDD 5.2) can't be guessed or enumerated, unlike 1, 2, 3...
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "member_id")
    private UUID id;

    @NotBlank
    @Size(max = 120)
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    // @Convert: stored encrypted (AES-256-GCM); this field always holds the plain 13-digit number
    @NotBlank
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id", nullable = false, length = 255)
    private String nationalId;

    // Keyed hash of the ID number: lets the database enforce uniqueness without seeing the ID
    @NotBlank
    @Column(name = "national_id_hash", nullable = false, unique = true, length = 64)
    private String nationalIdHash;

    // Normalised to 10 digits, e.g. 0821234567 (see PhoneNumbers). Doubles as the login name.
    @NotBlank
    @Size(max = 15)
    @Column(name = "phone_number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @Size(max = 120)
    @Column(length = 120)
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private NotificationChannel preferredChannel = NotificationChannel.SMS;

    // PBKDF2 hash (never the password itself). New members get an unusable random hash until
    // they sign in with an SMS code and choose a password.
    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_set", nullable = false)
    private boolean passwordSet;

    // Coop Office administrator (platform-wide role, not tied to a group)
    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    // Account lockout after repeated failed logins (SDD 4.6)
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // One-time login code (hashed) and when it stops working
    @Column(name = "otp_hash", length = 255)
    private String otpHash;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    // POPIA: when the member consented to their information being processed
    @NotNull
    @Column(name = "popia_consent_at", nullable = false)
    private LocalDateTime popiaConsentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // @PrePersist: JPA calls this just before the row is first inserted
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getNationalIdHash() {
        return nationalIdHash;
    }

    public void setNationalIdHash(String nationalIdHash) {
        this.nationalIdHash = nationalIdHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public NotificationChannel getPreferredChannel() {
        return preferredChannel;
    }

    public void setPreferredChannel(NotificationChannel preferredChannel) {
        this.preferredChannel = preferredChannel;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public void setPasswordSet(boolean passwordSet) {
        this.passwordSet = passwordSet;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public LocalDateTime getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public LocalDateTime getPopiaConsentAt() {
        return popiaConsentAt;
    }

    public void setPopiaConsentAt(LocalDateTime popiaConsentAt) {
        this.popiaConsentAt = popiaConsentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
}
