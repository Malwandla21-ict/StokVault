package com.stokvault.dto;

import com.stokvault.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditEntryView(UUID id, long sequence, String action, String performedBy, String entityType,
                             String entityId, String details, LocalDateTime loggedAt, String previousHash,
                             String entryHash) {

    public static AuditEntryView from(AuditLog a) {
        return new AuditEntryView(a.getId(), a.getSequenceNumber(), a.getAction(),
                a.getPerformedBy() == null ? "System" : a.getPerformedBy().getFullName(),
                a.getEntityType(), a.getEntityId(), a.getDetails(), a.getLoggedAt(), a.getPreviousHash(),
                a.getEntryHash());
    }
}
