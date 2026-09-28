package com.stokvault.batch;

import jakarta.batch.api.chunk.ItemProcessor;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.UUID;

/**
 * Chunk step, part 2 of 3: evaluates the eligibility rules for one payout.
 */
@Named
@Dependent // a CDI scope, so the bean is discovered (with an empty beans.xml, @Named alone is not enough)
public class EligibilityProcessor implements ItemProcessor {

    @Inject
    private EligibilityService eligibility;

    @Override
    public Object processItem(Object payoutId) {
        return eligibility.evaluate((UUID) payoutId);
    }
}
