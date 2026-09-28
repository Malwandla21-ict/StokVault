package com.stokvault.audit;

import com.stokvault.domain.AuditAction;
import com.stokvault.domain.HashChain;
import com.stokvault.entity.AuditLog;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.security.AccessControl;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Appends entries to a group's hash-chained audit log (SDD 4.5).
 *
 * It runs inside the caller's transaction (the EJB default, REQUIRED), so the financial change
 * and its audit entry commit or roll back together: a change can never exist without its entry.
 */
@Stateless
public class AuditService {

    private static final Jsonb JSONB = JsonbBuilder.create();

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private AccessControl access;

    /**
     * @param details before/after values or other context, stored as JSON (keys sorted, so the
     *                text is stable)
     */
    public AuditLog record(StokvelGroup group, AuditAction action, String entityType, Object entityId,
                           Map<String, ?> details) {
        // Lock the group's row for the rest of the transaction: two actions in the same group
        // can't both read "last entry = 41" and both append entry 42. Other groups aren't blocked.
        // (flush first: a group created in this same transaction must exist in the table to be locked)
        em.flush();
        em.lock(group, LockModeType.PESSIMISTIC_WRITE);

        AuditLog last = em.createQuery(
                        "SELECT a FROM AuditLog a WHERE a.group = :group ORDER BY a.sequenceNumber DESC", AuditLog.class)
                .setParameter("group", group)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
        long sequence = last == null ? 1 : last.getSequenceNumber() + 1;
        String previousHash = last == null ? HashChain.GENESIS : last.getEntryHash();
        String json = details == null || details.isEmpty() ? null : JSONB.toJson(new TreeMap<>(details));

        AuditLog entry = new AuditLog(group, sequence, action.name(), access.currentMemberOrSystem(),
                entityType, entityId == null ? null : entityId.toString(), json,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS), previousHash);
        em.persist(entry);
        return entry;
    }

    /** Newest first. */
    public List<AuditLog> entries(StokvelGroup group, int limit) {
        return em.createQuery("SELECT a FROM AuditLog a WHERE a.group = :group ORDER BY a.sequenceNumber DESC", AuditLog.class)
                .setParameter("group", group)
                .setMaxResults(limit)
                .getResultList();
    }

    /** Recomputes the whole chain from the stored data (SDD 4.5 "verification job"). */
    public HashChain.Verification verify(StokvelGroup group) {
        List<HashChain.StoredEntry> chain = em.createQuery(
                        "SELECT a FROM AuditLog a WHERE a.group = :group ORDER BY a.sequenceNumber", AuditLog.class)
                .setParameter("group", group)
                // Read straight from the database, not JPA's cache, so tampering done directly
                // in PostgreSQL is seen
                .setHint("jakarta.persistence.cache.retrieveMode", jakarta.persistence.CacheRetrieveMode.BYPASS)
                .setHint("eclipselink.refresh", "true")
                .getResultStream()
                .map(a -> new HashChain.StoredEntry(a.toChainEntry(), a.getEntryHash()))
                .toList();
        return HashChain.verify(chain);
    }
}
