package com.stokvault.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The hash chain behind the tamper-evident audit log (SDD 4.5).
 *
 * Each entry's hash covers its own content AND the previous entry's hash:
 *   entry_hash = SHA-256(previous_hash | sequence | group_id | action | performed_by | timestamp | entity | details)
 * Changing or deleting any historical entry changes its hash, which no longer matches the
 * previous_hash stored in the next entry, so every entry after it fails verification.
 */
public final class HashChain {

    /** previous_hash of the first entry in each group's chain. */
    public static final String GENESIS = "0".repeat(64);

    /** Timestamps are hashed at millisecond precision, the same precision they're stored at. */
    public static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private HashChain() {
    }

    /** The content of one audit entry, in the form that gets hashed. */
    public record Entry(String previousHash, long sequence, UUID groupId, String action, UUID performedBy,
                        LocalDateTime timestamp, String entityType, String entityId, String details) {

        String canonical() {
            return String.join("|",
                    previousHash,
                    Long.toString(sequence),
                    groupId.toString(),
                    action,
                    performedBy == null ? "SYSTEM" : performedBy.toString(),
                    TIMESTAMP.format(timestamp),
                    Objects.toString(entityType, ""),
                    Objects.toString(entityId, ""),
                    Objects.toString(details, ""));
        }
    }

    public static String hash(Entry entry) {
        return sha256(entry.canonical());
    }

    /** One stored entry: its content plus the hash that was saved with it. */
    public record StoredEntry(Entry entry, String storedHash) {
    }

    /** Result of checking a whole chain. brokenAtSequence is null when the chain is intact. */
    public record Verification(boolean valid, int entriesChecked, Long brokenAtSequence, String message) {
    }

    /** Recomputes the chain from the start and reports the first entry that doesn't match. */
    public static Verification verify(List<StoredEntry> entries) {
        String expectedPrevious = GENESIS;
        long expectedSequence = 1;
        for (StoredEntry stored : entries) {
            Entry e = stored.entry();
            if (e.sequence() != expectedSequence) {
                return new Verification(false, (int) (expectedSequence - 1), expectedSequence,
                        "Entry " + expectedSequence + " is missing (found " + e.sequence() + " next)");
            }
            if (!e.previousHash().equals(expectedPrevious)) {
                return new Verification(false, (int) (expectedSequence - 1), e.sequence(),
                        "Entry " + e.sequence() + " does not link to the entry before it");
            }
            if (!hash(e).equals(stored.storedHash())) {
                return new Verification(false, (int) (expectedSequence - 1), e.sequence(),
                        "Entry " + e.sequence() + " has been altered (its content no longer matches its hash)");
            }
            expectedPrevious = stored.storedHash();
            expectedSequence++;
        }
        return new Verification(true, entries.size(), null, "All " + entries.size() + " entries are intact");
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
