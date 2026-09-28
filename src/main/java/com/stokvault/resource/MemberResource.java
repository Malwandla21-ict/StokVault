package com.stokvault.resource;

import com.stokvault.dto.MemberRequest;
import com.stokvault.dto.MemberResponse;
import com.stokvault.dto.MembershipResponse;
import com.stokvault.service.MemberService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
 * REST endpoints for members, served under /stokvault/api/members.
 * This class only deals with HTTP; the real work is in MemberService.
 */
@Path("/members")
// Set at class level, so every method reads and writes JSON
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MemberResource {

    // @Inject: CDI (Contexts and Dependency Injection) supplies the MemberService
    // instance. CDI is enabled by the beans.xml file in WEB-INF.
    @Inject
    private MemberService memberService;

    // GET /api/members            -> every member
    // GET /api/members?search=tha -> members whose name or email contains "tha"
    // @QueryParam reads a ?name=value parameter from the URL (null if it's missing)
    @GET
    public List<MemberResponse> list(@QueryParam("search") String search) {
        return memberService.findAll(search).stream().map(MemberResponse::from).toList();
    }

    // GET /api/members/5 -> one member, or 404 if there's no member 5.
    // {id} in @Path is a placeholder; @PathParam("id") copies it into the parameter.
    @GET
    @Path("/{id}")
    public MemberResponse get(@PathParam("id") Long id) {
        return MemberResponse.from(memberService.find(id));
    }

    // POST /api/members with body {"name":"Thandi","email":"thandi@example.com"}
    // @Valid: run the Bean Validation rules on the request first. If they fail, the
    // client gets a 400 listing the problems and this method never runs.
    // @NotNull: an empty body is also a 400.
    @POST
    public Response create(@Valid @NotNull MemberRequest request, @Context UriInfo uriInfo) {
        MemberResponse created = MemberResponse.from(memberService.create(request));
        // 201 Created, with a Location header pointing at the new member
        URI location = uriInfo.getAbsolutePathBuilder().path(created.id().toString()).build();
        return Response.created(location).entity(created).build();
    }

    // PUT /api/members/5 replaces the member's details
    @PUT
    @Path("/{id}")
    public MemberResponse update(@PathParam("id") Long id, @Valid @NotNull MemberRequest request) {
        return MemberResponse.from(memberService.update(id, request));
    }

    // DELETE /api/members/5 -> 204 No Content. Refused (409) once they've joined a stokvel.
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        memberService.delete(id);
        return Response.noContent().build();
    }

    // GET /api/members/5/memberships -> the stokvels this member belongs to
    @GET
    @Path("/{id}/memberships")
    public List<MembershipResponse> memberships(@PathParam("id") Long id) {
        return memberService.memberships(id).stream().map(MembershipResponse::from).toList();
    }
}
