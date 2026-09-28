package com.stokvault.resource;

import com.stokvault.domain.StokvelStatus;
import com.stokvault.dto.StokvelRequest;
import com.stokvault.dto.StokvelResponse;
import com.stokvault.dto.StokvelSummary;
import com.stokvault.service.ReportService;
import com.stokvault.service.StokvelService;
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
 * REST endpoints for stokvels, under /api/stokvels.
 * Members, contributions and payouts of a stokvel have their own resource classes
 * under /api/stokvels/{stokvelId}/...
 */
@Path("/stokvels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StokvelResource {

    @Inject
    private StokvelService stokvelService;

    @Inject
    private ReportService reportService;

    // GET /api/stokvels?status=ACTIVE (status is optional). JAX-RS converts the text to the enum.
    @GET
    public List<StokvelResponse> list(@QueryParam("status") StokvelStatus status) {
        return stokvelService.findAll(status).stream().map(StokvelResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    public StokvelResponse get(@PathParam("id") Long id) {
        return StokvelResponse.from(stokvelService.find(id));
    }

    @POST
    public Response create(@Valid @NotNull StokvelRequest request, @Context UriInfo uriInfo) {
        StokvelResponse created = StokvelResponse.from(stokvelService.create(request));
        URI location = uriInfo.getAbsolutePathBuilder().path(created.id().toString()).build();
        return Response.created(location).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public StokvelResponse update(@PathParam("id") Long id, @Valid @NotNull StokvelRequest request) {
        return StokvelResponse.from(stokvelService.update(id, request));
    }

    // Only allowed while the stokvel has no contributions or payouts; otherwise close it
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        stokvelService.delete(id);
        return Response.noContent().build();
    }

    // POST /api/stokvels/1/close (no body). An action that isn't plain create/update/delete
    // is modelled as a POST to a sub-path.
    @POST
    @Path("/{id}/close")
    public StokvelResponse close(@PathParam("id") Long id) {
        return StokvelResponse.from(stokvelService.close(id));
    }

    @POST
    @Path("/{id}/reopen")
    public StokvelResponse reopen(@PathParam("id") Long id) {
        return StokvelResponse.from(stokvelService.reopen(id));
    }

    // GET /api/stokvels/1/summary -> totals, balance, and each member's arrears
    @GET
    @Path("/{id}/summary")
    public StokvelSummary summary(@PathParam("id") Long id) {
        return reportService.summary(id);
    }
}
