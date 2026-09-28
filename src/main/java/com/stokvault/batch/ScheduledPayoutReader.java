package com.stokvault.batch;

import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Jakarta Batch chunk step, part 1 of 3: reads the ids of the payouts to check, one at a time.
 * Named "scheduledPayoutReader" in META-INF/batch-jobs/payout-eligibility.xml.
 */
@Named
@Dependent // a CDI scope, so the bean is discovered (with an empty beans.xml, @Named alone is not enough)
public class ScheduledPayoutReader extends AbstractItemReader {

    // Filled from the job XML, which passes on the job parameter "groupId" (blank = all groups)
    @Inject
    @BatchProperty
    private String groupId;

    @Inject
    private EligibilityService eligibility;

    private List<UUID> payoutIds;
    private int next;

    @Override
    public void open(Serializable checkpoint) {
        payoutIds = eligibility.payoutsToCheck(groupId == null || groupId.isBlank() ? null : UUID.fromString(groupId));
        // On restart after a failure, carry on where the last committed chunk ended
        next = checkpoint == null ? 0 : (Integer) checkpoint;
    }

    @Override
    public Object readItem() {
        return next < payoutIds.size() ? payoutIds.get(next++) : null; // null = no more items
    }

    @Override
    public Serializable checkpointInfo() {
        return next;
    }
}
