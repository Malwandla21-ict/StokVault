package com.stokvault.entity;

import com.stokvault.domain.HashChain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry in a group's tamper-evident audit trail (SDD 4.5, 5.2 "AuditLog").
 *
 * Append-only, enforced three ways:
 *  1. this class has no setters and refuses updates/deletes (@PreUpdate/@PreRemove),
 *  2. a database trigger rejects UPDATE, DELETE and TRUNCATE on the table (see DatabaseSetup),
 *  3. each entry's hash covers the previous entry's hash, so any change that slips past 1 and 2
 *     is detected by the chain verification.
 */
@Entity
@Table(name = "audit_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "sequence_number"}),
        indexes = @Index(name = "idx_audit_group_seq", columnList = "group_id, sequence_number"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id")
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private StokvelGroup group;

    // 1, 2, 3... within each group's chain
    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(nullable = false, updatable = false, length = 50)
    private String action;

    // Null when the system acted (the batch job, a scheduled task)
    @ManyToOne
    @JoinColumn(name = "performed_by", updatable = false)
    private Member performedBy;

    @Column(name = "entity_type", updatable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 64)
    private String entityId;

    // Before/after state as JSON
    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private LocalDateTime loggedAt;

    @Column(name = "previous_hash", nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, updatable = false, length = 64)
    private String entryHash;

    protected AuditLog() {
        // for JPA
    }

    /** Builds the next entry of a group's chain and computes its hash. */
    public AuditLog(StokvelGroup group, long sequenceNumber, String action, Member performedBy,
                    String entityType, String entityId, String details, LocalDateTime loggedAt,
                    String previousHash) {
        this.group = group;
        this.sequenceNumber = sequenceNumber;
        this.action = action;
        this.performedBy = performedBy;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.loggedAt = loggedAt;
        this.previousHash = previousHash;
        this.entryHash = HashChain.hash(toChainEntry());
    }

    /** The content covered by the hash, rebuilt from what's stored (used for verification). */
    public HashChain.Entry toChainEntry() {
        return new HashChain.Entry(previousHash, sequenceNumber, group.getId(), action,
                performedBy == null ? null : performedBy.getId(), loggedAt, entityType, entityId, details);
    }

    @PreUpdate
    @PreRemove
    void refuseChanges() {
        throw new IllegalStateException("Audit log entries can never be changed or deleted");
    }

    public UUID getId() {
        return id;
    }

    public StokvelGroup getGroup() {
        return group;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getAction() {
        return action;
    }

    public Member getPerformedBy() {
        return performedBy;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }
}
