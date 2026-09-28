package com.stokvault.resource;

import com.stokvault.dto.MemberRegistration;
import com.stokvault.dto.MemberUpdate;
import com.stokvault.dto.MemberView;
import com.stokvault.dto.MembershipView;
import com.stokvault.service.MemberService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
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
 * Registered people: /api/members
 * (Joining a group is done under /api/groups/{groupId}/members.)
 */
// @Path: the URL, relative to @ApplicationPath("/api"). @Produces/@Consumes: JSON in and out,
// converted automatically by JSON-B.
@Path("/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MemberResource {

    // @Inject: CDI supplies the EJB (CDI is switched on by WEB-INF/beans.xml)
    @Inject
    private MemberService members;

    // GET /api/members?q=0821234567. Admins can search by name too; officers by exact phone or ID number.
    @GET
    public List<MemberView> search(@QueryParam("q") String query) {
        return members.search(query).stream().map(MemberView::from).toList();
    }

    // @Valid runs the Bean Validation rules on the body first (400 if they fail)
    @POST
    public Response register(@Valid @NotNull MemberRegistration registration, @Context UriInfo uri) {
        MemberView created = MemberView.from(members.register(registration));
        return Response.created(uri.getAbsolutePathBuilder().path(created.id().toString()).build()).entity(created).build();
    }

    // {id} is a path placeholder; JAX-RS converts it to a UUID (an invalid one gives 404)
    @GET
    @Path("/{id}")
    public MemberView get(@PathParam("id") UUID id) {
        return MemberView.from(members.find(id));
    }

    @PUT
    @Path("/{id}")
    public MemberView update(@PathParam("id") UUID id, @Valid @NotNull MemberUpdate update) {
        return MemberView.from(members.update(id, update));
    }

    @GET
    @Path("/{id}/memberships")
    public List<MembershipView> memberships(@PathParam("id") UUID id) {
        return members.memberships(id).stream().map(MembershipView::from).toList();
    }
}
