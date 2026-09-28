package com.stokvault.batch;

import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

/**
 * Chunk step, part 3 of 3: saves a chunk of outcomes. The batch runtime commits each chunk in its
 * own transaction, so a failure part-way only repeats the unfinished chunk.
 */
@Named
@Dependent // a CDI scope, so the bean is discovered (with an empty beans.xml, @Named alone is not enough)
public class EligibilityWriter extends AbstractItemWriter {

    @Inject
    private EligibilityService eligibility;

    @Override
    public void writeItems(List<Object> items) {
        eligibility.record(items.stream().map(EligibilityService.Outcome.class::cast).toList());
    }
}
