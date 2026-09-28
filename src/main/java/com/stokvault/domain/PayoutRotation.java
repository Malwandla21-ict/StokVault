package com.stokvault.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides whose turn it is in a ROTATING stokvel. Plain Java, unit tested in PayoutRotationTest.
 */
public final class PayoutRotation {

    private PayoutRotation() {
    }

    /** One active member's place in the rotation. */
    public record Slot(long memberId, int payoutPosition, long payoutsReceived) {
    }

    /**
     * The next recipient is whoever has received the fewest payouts so far; ties go to the
     * earliest payout position. Once everyone has had a turn the counts are equal again,
     * so a new cycle starts from the front of the order.
     */
    public static Optional<Slot> next(List<Slot> slots) {
        return slots.stream()
                .min(Comparator.comparingLong(Slot::payoutsReceived)
                        .thenComparingInt(Slot::payoutPosition));
    }
}
