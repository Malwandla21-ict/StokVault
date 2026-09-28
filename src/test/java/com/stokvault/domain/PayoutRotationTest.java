package com.stokvault.domain;

import com.stokvault.domain.PayoutRotation.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutRotationTest {

    @Test
    void noMembersMeansNoRecipient() {
        assertTrue(PayoutRotation.next(List.of()).isEmpty());
    }

    @Test
    void firstPayoutGoesToPositionOne() {
        List<Slot> slots = List.of(new Slot(10, 2, 0), new Slot(11, 1, 0), new Slot(12, 3, 0));
        assertEquals(11, PayoutRotation.next(slots).orElseThrow().memberId());
    }

    @Test
    void membersWhoHaveBeenPaidWaitForEveryoneElse() {
        List<Slot> slots = List.of(new Slot(10, 1, 1), new Slot(11, 2, 1), new Slot(12, 3, 0));
        assertEquals(12, PayoutRotation.next(slots).orElseThrow().memberId());
    }

    @Test
    void aNewCycleStartsAgainFromTheFront() {
        List<Slot> slots = List.of(new Slot(10, 1, 1), new Slot(11, 2, 1), new Slot(12, 3, 1));
        assertEquals(10, PayoutRotation.next(slots).orElseThrow().memberId());
    }

    @Test
    void aMemberWhoJoinedLateIsPaidBeforeTheSecondCycle() {
        // Position 4 joined after the first cycle had started and hasn't been paid yet
        List<Slot> slots = List.of(new Slot(10, 1, 2), new Slot(11, 2, 1), new Slot(12, 3, 1), new Slot(13, 4, 0));
        assertEquals(13, PayoutRotation.next(slots).orElseThrow().memberId());
    }
}
