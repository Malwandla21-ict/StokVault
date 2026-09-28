package com.stokvault.resource;

import com.stokvault.audit.AuditService;
import com.stokvault.domain.HashChain;
import com.stokvault.domain.MembershipRole;
import com.stokvault.dto.AuditEntryView;
import com.stokvault.dto.GroupRequest;
import com.stokvault.dto.GroupSummary;
import com.stokvault.dto.GroupView;
import com.stokvault.dto.NotificationView;
import com.stokvault.dto.OptionalReason;
import com.stokvault.entity.StokvelGroup;
import com.stokvault.security.AccessControl;
import com.stokvault.service.GroupService;
import com.stokvault.service.NotificationLogService;
import com.stokvault.service.ReportService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import java.util.UUID;

/**
 * Stokvel groups: /api/groups
 */
@Path("/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GroupResource {

    @Inject
    private GroupService groups;

    @Inject
    private ReportService reports;

    @Inject
    private AuditService audit;

    @Inject
    private AccessControl access;

    @Inject
    private NotificationLogService notificationLog;

    @GET
    public List<GroupView> list() {
        return groups.list();
    }

    // Admin only (enforced by @RolesAllowed on GroupService.create)
    @POST
    public Response create(@Valid @NotNull GroupRequest request, @Context UriInfo uri) {
        GroupView created = groups.view(groups.create(request));
        return Response.created(uri.getAbsolutePathBuilder().path(created.id().toString()).build()).entity(created).build();
    }

    @GET
    @Path("/{id}")
    public GroupView get(@PathParam("id") UUID id) {
        return groups.view(groups.find(id));
    }

    @PUT
    @Path("/{id}")
    public GroupView update(@PathParam("id") UUID id, @Valid @NotNull GroupRequest request) {
        return groups.view(groups.update(id, request));
    }

    @POST
    @Path("/{id}/activate")
    public GroupView activate(@PathParam("id") UUID id) {
        return groups.view(groups.activate(id));
    }

    @POST
    @Path("/{id}/suspend")
    public GroupView suspend(@PathParam("id") UUID id, String body) {
        return groups.view(groups.suspend(id, OptionalReason.parse(body)));
    }

    @POST
    @Path("/{id}/resume")
    public GroupView resume(@PathParam("id") UUID id) {
        return groups.view(groups.resume(id));
    }

    @POST
    @Path("/{id}/close")
    public GroupView close(@PathParam("id") UUID id, String body) {
        return groups.view(groups.close(id, OptionalReason.parse(body)));
    }

    /** Balance, totals, current cycle, next rotation payout and member standings. */
    @GET
    @Path("/{id}/summary")
    public GroupSummary summary(@PathParam("id") UUID id) {
        return reports.summary(id);
    }

    /** The newest audit entries (treasurer, committee, admin). */
    @GET
    @Path("/{id}/audit")
    public List<AuditEntryView> auditTrail(@PathParam("id") UUID id, @QueryParam("limit") @DefaultValue("100") int limit) {
        StokvelGroup group = officerView(id);
        return audit.entries(group, Math.min(Math.max(limit, 1), 1000)).stream().map(AuditEntryView::from).toList();
    }

    /** Recomputes the hash chain: {"valid":true,"entriesChecked":42,...} */
    @GET
    @Path("/{id}/audit/verify")
    public HashChain.Verification verifyAudit(@PathParam("id") UUID id) {
        return audit.verify(officerView(id));
    }

    @GET
    @Path("/{id}/notifications")
    public List<NotificationView> notifications(@PathParam("id") UUID id) {
        return notificationLog.forGroup(id).stream().map(NotificationView::from).toList();
    }

    private StokvelGroup officerView(UUID id) {
        StokvelGroup group = groups.find(id);
        access.requireRoleOrAdmin(group, MembershipRole.TREASURER, MembershipRole.COMMITTEE);
        return group;
    }
}
