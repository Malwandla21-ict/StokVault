package com.stokvault.batch;

import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

import java.util.logging.Logger;

/**
 * Re-checks every scheduled payout each morning, so results stay current as contributions are
 * verified (the SDD's "automated scheduler" actor).
 */
@Singleton
public class NightlyJobs {

    private static final Logger LOG = Logger.getLogger(NightlyJobs.class.getName());

    @Inject
    private EligibilityJobs jobs;

    // @Schedule: an EJB timer, like cron. persistent = false: if the server was down at 06:00,
    // don't try to catch up on missed runs when it starts again.
    @Schedule(hour = "6", minute = "0", persistent = false)
    public void checkAllScheduledPayouts() {
        long executionId = jobs.start(null);
        LOG.info("Started nightly payout eligibility check, execution " + executionId);
    }
}
