package com.stokvault.batch;

import java.util.UUID;

/** CDI event: new payouts were scheduled in this group and need an eligibility check. */
public record PayoutsScheduled(UUID groupId) {
}
