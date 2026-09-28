package com.stokvault.resource;

import com.stokvault.batch.EligibilityJobs;
import com.stokvault.domain.NotificationStatus;
import com.stokvault.dto.NotificationView;
import com.stokvault.dto.PlatformReport;
import com.stokvault.service.AdminService;
import com.stokvault.service.NotificationLogService;
import com.stokvault.service.ReportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Coop Office administration: /api/admin (ADMIN role; enforced by the services' @RolesAllowed)
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    private ReportService reports;

    @Inject
    private NotificationLogService notificationLog;

    @Inject
    private AdminService admin;

    /** Platform-wide report: every group's balance, arrears and audit-chain status. */
    @GET
    @Path("/report")
    public PlatformReport report() {
        return reports.platform();
    }

    /** ?status=FAILED lists the dead-lettered notifications needing manual follow-up. */
    @GET
    @Path("/notifications")
    public List<NotificationView> notifications(@QueryParam("status") NotificationStatus status) {
        return notificationLog.all(status).stream().map(NotificationView::from).toList();
    }

    @POST
    @Path("/jobs/eligibility")
    public Response runEligibility() {
        return Response.accepted(Map.of("executionId", admin.startEligibilityCheckForAllGroups())).build();
    }

    @GET
    @Path("/jobs/{executionId}")
    public EligibilityJobs.Status job(@PathParam("executionId") long executionId) {
        return admin.jobStatus(executionId);
    }

    @POST
    @Path("/jobs/reminders")
    public Map<String, Integer> sendReminders() {
        return Map.of("remindersQueued", admin.sendRemindersNow());
    }
}
