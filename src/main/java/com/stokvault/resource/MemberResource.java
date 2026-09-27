package com.stokvault.resource;

import com.stokvault.entity.Member;
import com.stokvault.service.MemberService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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

    // GET /api/members -> JSON array of all members
    @GET
    public List<Member> list() {
        return memberService.findAll();
    }

    // GET /api/members/5 -> one member, or 404 if there's no member 5.
    // {id} in @Path is a placeholder; @PathParam("id") copies it into the parameter.
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return memberService.findById(id)
                .map(member -> Response.ok(member).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    // POST /api/members with body {"name":"Thandi","email":"thandi@example.com"}
    // @Valid: run the Bean Validation rules on Member first. If they fail, the
    // server replies 400 Bad Request and this method never runs.
    @POST
    public Response create(@Valid Member member, @Context UriInfo uriInfo) {
        Member created = memberService.create(member);
        // 201 Created, with a Location header pointing at the new member
        URI location = uriInfo.getAbsolutePathBuilder().path(created.getId().toString()).build();
        return Response.created(location).entity(created).build();
    }
}
