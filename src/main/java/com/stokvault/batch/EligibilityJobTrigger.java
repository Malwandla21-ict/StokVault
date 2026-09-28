package com.stokvault.batch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts the eligibility check as soon as a payout run has committed.
 *
 * during = AFTER_SUCCESS: CDI delivers the event only after the transaction that fired it commits,
 * so the batch job (which runs on its own thread) can see the new payouts. If the payout run
 * rolled back, nothing starts.
 */
@ApplicationScoped
public class EligibilityJobTrigger {

    private static final Logger LOG = Logger.getLogger(EligibilityJobTrigger.class.getName());

    @Inject
    private EligibilityJobs jobs;

    void onPayoutsScheduled(@Observes(during = TransactionPhase.AFTER_SUCCESS) PayoutsScheduled event) {
        try {
            jobs.start(event.groupId());
        } catch (RuntimeException e) {
            // The nightly run will pick the payouts up, and treasurers can start a check by hand
            LOG.log(Level.WARNING, "Could not start the eligibility check for group " + event.groupId(), e);
        }
    }
}
