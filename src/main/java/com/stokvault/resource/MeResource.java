package com.stokvault.resource;

import com.stokvault.dto.AuthRequests;
import com.stokvault.dto.MemberView;
import com.stokvault.dto.MyPosition;
import com.stokvault.dto.NotificationView;
import com.stokvault.service.AuthService;
import com.stokvault.service.MemberService;
import com.stokvault.service.NotificationLogService;
import com.stokvault.service.ReportService;
import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The logged-in member: /api/me
 */
@Path("/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    private MemberService members;

    @Inject
    private ReportService reports;

    @Inject
    private AuthService auth;

    @Inject
    private NotificationLogService notificationLog;

    @Inject
    private SecurityContext securityContext;

    /** Who am I, and which roles do I have? */
    @GET
    public Map<String, Object> me() {
        List<String> roles = Stream.of("ADMIN", "TREASURER", "COMMITTEE", "MEMBER")
                .filter(securityContext::isCallerInRole).toList();
        return Map.of("member", MemberView.from(members.me()), "roles", roles);
    }

    /** The member dashboard: my position in each of my groups. */
    @GET
    @Path("/positions")
    public List<MyPosition> positions() {
        return reports.myPositions();
    }

    @GET
    @Path("/notifications")
    public List<NotificationView> notifications() {
        return notificationLog.mine().stream().map(NotificationView::from).toList();
    }

    /** {"currentPassword":"...","newPassword":"..."} (currentPassword not needed the first time) */
    @PUT
    @Path("/password")
    public Response changePassword(@Valid @NotNull AuthRequests.PasswordChange change) {
        auth.changePassword(change);
        return Response.noContent().build();
    }
}
