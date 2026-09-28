package com.stokvault.config;

import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.PhoneNumbers;
import com.stokvault.domain.SaIdNumber;
import com.stokvault.entity.Member;
import com.stokvault.security.FieldCrypto;
import com.stokvault.security.PasswordHasher;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Runs once each time the application starts.
 *
 * 1. Makes the audit_log table append-only at the database level (SDD 4.5): a trigger rejects
 *    UPDATE, DELETE and TRUNCATE, whoever runs them. This is independent of the Java code, so even
 *    someone editing rows directly in pgAdmin can't quietly rewrite history.
 * 2. Creates the first Coop Office administrator if there is none yet. Its phone number is
 *    stokvault.admin.phone (default 0600000000); the password is stokvault.admin.password, or,
 *    if that isn't set, a random one printed once in the server log.
 */
// @Singleton @Startup: one instance, created (and @PostConstruct run) when the app is deployed
@Singleton
@Startup
public class StartupTasks {

    private static final Logger LOG = Logger.getLogger(StartupTasks.class.getName());

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private PasswordHasher hasher;

    @PostConstruct
    void onStartup() {
        protectAuditLog();
        createFirstAdmin();
    }

    private void protectAuditLog() {
        em.createNativeQuery("""
                CREATE OR REPLACE FUNCTION stokvault_audit_log_is_append_only() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'audit_log is append-only: entries can never be changed or deleted';
                END;
                $$ LANGUAGE plpgsql""").executeUpdate();
        em.createNativeQuery("""
                CREATE OR REPLACE TRIGGER audit_log_no_update_or_delete
                BEFORE UPDATE OR DELETE ON audit_log
                FOR EACH ROW EXECUTE FUNCTION stokvault_audit_log_is_append_only()""").executeUpdate();
        em.createNativeQuery("""
                CREATE OR REPLACE TRIGGER audit_log_no_truncate
                BEFORE TRUNCATE ON audit_log
                FOR EACH STATEMENT EXECUTE FUNCTION stokvault_audit_log_is_append_only()""").executeUpdate();
    }

    private void createFirstAdmin() {
        long admins = em.createQuery("SELECT COUNT(m) FROM Member m WHERE m.admin = TRUE", Long.class).getSingleResult();
        if (admins > 0) {
            return;
        }
        String configuredPhone = PhoneNumbers.normalise(System.getProperty("stokvault.admin.phone"));
        String phone = configuredPhone != null ? configuredPhone : "0600000000";
        String password = System.getProperty("stokvault.admin.password");
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = randomPassword();
        }
        // The administrator is a platform account, not a real person, so it gets a placeholder
        // (but valid) ID number: date of birth 2000-01-01, sequence 0000
        String placeholderId = "000101000008";
        placeholderId = placeholderId + SaIdNumber.luhnCheckDigit(placeholderId);

        Member admin = new Member();
        admin.setFullName("Coop Office Administrator");
        admin.setNationalId(placeholderId);
        admin.setNationalIdHash(FieldCrypto.get().lookupHash(placeholderId));
        admin.setPhoneNumber(phone);
        admin.setPreferredChannel(NotificationChannel.SMS);
        admin.setPasswordHash(hasher.hash(password));
        admin.setPasswordSet(true);
        admin.setAdmin(true);
        admin.setPopiaConsentAt(LocalDateTime.now());
        em.persist(admin);

        LOG.warning("Created the first StokVault administrator. Log in with phone " + phone
                + (generated ? " and this one-time generated password: " + password : " and the configured stokvault.admin.password")
                + ". Change the password after logging in.");
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder("Sv");
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.append(random.nextInt(10)).toString();
    }
}
