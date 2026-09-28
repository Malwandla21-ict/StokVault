package com.stokvault.service;

import com.stokvault.batch.EligibilityJobs;
import com.stokvault.notification.ReminderService;
import com.stokvault.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * Coop Office operations: running the background jobs on demand (normally they run on schedule).
 */
@Stateless
@RolesAllowed(Roles.ADMIN)
public class AdminService {

    @Inject
    private EligibilityJobs eligibilityJobs;

    @Inject
    private ReminderService reminders;

    public long startEligibilityCheckForAllGroups() {
        return eligibilityJobs.start(null);
    }

    public EligibilityJobs.Status jobStatus(long executionId) {
        return eligibilityJobs.status(executionId);
    }

    public int sendRemindersNow() {
        return reminders.sendDueReminders();
    }
}
