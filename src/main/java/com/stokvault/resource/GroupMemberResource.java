package com.stokvault.resource;

import com.stokvault.dto.MembershipRequest;
import com.stokvault.dto.MembershipUpdate;
import com.stokvault.dto.MembershipView;
import com.stokvault.service.MembershipService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
 * A group's members: /api/groups/{groupId}/members
 */
@Path("/groups/{groupId}/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GroupMemberResource {

    @Inject
    private MembershipService memberships;

    @GET
    public List<MembershipView> list(@PathParam("groupId") UUID groupId,
                                     @QueryParam("includeInactive") @DefaultValue("false") boolean includeInactive) {
        return memberships.list(groupId, includeInactive).stream().map(MembershipView::from).toList();
    }

    // {"memberId":"...","role":"TREASURER","joinedDate":"2026-06-01"}
    @POST
    public Response add(@PathParam("groupId") UUID groupId, @Valid @NotNull MembershipRequest request, @Context UriInfo uri) {
        MembershipView added = MembershipView.from(memberships.add(groupId, request));
        return Response.created(uri.getAbsolutePathBuilder().path(added.memberId().toString()).build()).entity(added).build();
    }

    // {"role":"COMMITTEE"} and/or {"payoutPosition":2}
    @PUT
    @Path("/{memberId}")
    public MembershipView update(@PathParam("groupId") UUID groupId, @PathParam("memberId") UUID memberId,
                                 @Valid @NotNull MembershipUpdate update) {
        return MembershipView.from(memberships.update(groupId, memberId, update));
    }

    // The member leaves the group (history is kept)
    @DELETE
    @Path("/{memberId}")
    public MembershipView remove(@PathParam("groupId") UUID groupId, @PathParam("memberId") UUID memberId) {
        return MembershipView.from(memberships.remove(groupId, memberId));
    }
}
