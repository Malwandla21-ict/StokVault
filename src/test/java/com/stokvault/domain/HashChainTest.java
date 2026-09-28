package com.stokvault.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tamper-evidence guarantees of the audit log (SDD 4.5, 7.5).
 */
class HashChainTest {

    private static final UUID GROUP = UUID.randomUUID();
    private static final UUID TREASURER = UUID.randomUUID();
    private final List<HashChain.StoredEntry> chain = new ArrayList<>();

    @BeforeEach
    void buildChain() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 10, 0);
        String previous = HashChain.GENESIS;
        String[][] actions = {
                {"CONTRIBUTION_RECORDED", "{\"amount\":500.00}"},
                {"CONTRIBUTION_VERIFIED", "{\"amount\":500.00}"},
                {"PAYOUT_PAID", "{\"amount\":2500.00}"}};
        for (int i = 0; i < actions.length; i++) {
            HashChain.Entry entry = new HashChain.Entry(previous, i + 1, GROUP, actions[i][0], TREASURER,
                    t.plusMinutes(i), "Contribution", "id-" + i, actions[i][1]);
            String hash = HashChain.hash(entry);
            chain.add(new HashChain.StoredEntry(entry, hash));
            previous = hash;
        }
    }

    @Test
    void anUntouchedChainVerifies() {
        HashChain.Verification result = HashChain.verify(chain);
        assertTrue(result.valid());
        assertEquals(3, result.entriesChecked());
        assertNull(result.brokenAtSequence());
    }

    @Test
    void changingAnAmountIsDetected() {
        HashChain.Entry original = chain.get(0).entry();
        HashChain.Entry altered = new HashChain.Entry(original.previousHash(), original.sequence(), original.groupId(),
                original.action(), original.performedBy(), original.timestamp(), original.entityType(),
                original.entityId(), "{\"amount\":50.00}");
        chain.set(0, new HashChain.StoredEntry(altered, chain.get(0).storedHash()));

        HashChain.Verification result = HashChain.verify(chain);
        assertFalse(result.valid());
        assertEquals(1L, result.brokenAtSequence());
    }

    @Test
    void recomputingTheAlteredEntrysHashBreaksTheNextLink() {
        // A cleverer attacker also updates the stored hash of the entry they changed...
        HashChain.Entry original = chain.get(1).entry();
        HashChain.Entry altered = new HashChain.Entry(original.previousHash(), original.sequence(), original.groupId(),
                "CONTRIBUTION_REJECTED", original.performedBy(), original.timestamp(), original.entityType(),
                original.entityId(), original.details());
        chain.set(1, new HashChain.StoredEntry(altered, HashChain.hash(altered)));

        // ...but entry 3 still points at the old hash
        HashChain.Verification result = HashChain.verify(chain);
        assertFalse(result.valid());
        assertEquals(3L, result.brokenAtSequence());
    }

    @Test
    void deletingAnEntryIsDetected() {
        chain.remove(1);
        HashChain.Verification result = HashChain.verify(chain);
        assertFalse(result.valid());
        assertEquals(2L, result.brokenAtSequence());
    }

    @Test
    void theHashDependsOnWhoActed() {
        HashChain.Entry e = chain.get(0).entry();
        HashChain.Entry bySystem = new HashChain.Entry(e.previousHash(), e.sequence(), e.groupId(), e.action(), null,
                e.timestamp(), e.entityType(), e.entityId(), e.details());
        assertNotEquals(HashChain.hash(e), HashChain.hash(bySystem));
    }

    @Test
    void sha256IsHexOf64Characters() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashChain.sha256("abc"));
    }
}
