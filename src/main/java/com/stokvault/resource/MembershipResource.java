package com.stokvault.resource;

import com.stokvault.dto.MembershipRequest;
import com.stokvault.dto.MembershipResponse;
import com.stokvault.dto.MembershipUpdateRequest;
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

import java.net.URI;
import java.util.List;

/**
 * The members of one stokvel: /api/stokvels/{stokvelId}/members.
 * {stokvelId} in the class-level @Path is available to every method via @PathParam.
 */
@Path("/stokvels/{stokvelId}/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MembershipResource {

    @Inject
    private MembershipService membershipService;

    // GET .../members                      -> current members, in payout order
    // GET .../members?includeInactive=true -> also members who have left
    // @DefaultValue: used when the query parameter is missing
    @GET
    public List<MembershipResponse> list(@PathParam("stokvelId") Long stokvelId,
                                         @QueryParam("includeInactive") @DefaultValue("false") boolean includeInactive) {
        return membershipService.list(stokvelId, includeInactive).stream().map(MembershipResponse::from).toList();
    }

    @GET
    @Path("/{memberId}")
    public MembershipResponse get(@PathParam("stokvelId") Long stokvelId, @PathParam("memberId") Long memberId) {
        return MembershipResponse.from(membershipService.findActive(stokvelId, memberId));
    }

    // POST .../members with {"memberId":3,"role":"TREASURER"}
    @POST
    public Response add(@PathParam("stokvelId") Long stokvelId, @Valid @NotNull MembershipRequest request,
                        @Context UriInfo uriInfo) {
        MembershipResponse added = MembershipResponse.from(membershipService.add(stokvelId, request));
        URI location = uriInfo.getAbsolutePathBuilder().path(added.memberId().toString()).build();
        return Response.created(location).entity(added).build();
    }

    // PUT .../members/3 with {"role":"SECRETARY"} and/or {"payoutPosition":1}
    @PUT
    @Path("/{memberId}")
    public MembershipResponse update(@PathParam("stokvelId") Long stokvelId, @PathParam("memberId") Long memberId,
                                     @Valid @NotNull MembershipUpdateRequest request) {
        return MembershipResponse.from(membershipService.update(stokvelId, memberId, request));
    }

    // DELETE .../members/3 -> the member leaves. Returns the updated membership (with leftOn set).
    @DELETE
    @Path("/{memberId}")
    public MembershipResponse remove(@PathParam("stokvelId") Long stokvelId, @PathParam("memberId") Long memberId) {
        return MembershipResponse.from(membershipService.remove(stokvelId, memberId));
    }
}
