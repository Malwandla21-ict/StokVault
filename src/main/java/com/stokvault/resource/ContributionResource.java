package com.stokvault.resource;

import com.stokvault.dto.ContributionRequest;
import com.stokvault.dto.ContributionResponse;
import com.stokvault.service.ContributionService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Money paid into one stokvel: /api/stokvels/{stokvelId}/contributions.
 */
@Path("/stokvels/{stokvelId}/contributions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContributionResource {

    @Inject
    private ContributionService contributionService;

    // GET .../contributions?memberId=3&from=2026-06-01&to=2026-09-30 (all filters optional).
    // Dates are converted by LocalDateParamConverterProvider.
    @GET
    public List<ContributionResponse> list(@PathParam("stokvelId") Long stokvelId,
                                           @QueryParam("memberId") Long memberId,
                                           @QueryParam("from") LocalDate from,
                                           @QueryParam("to") LocalDate to) {
        return contributionService.list(stokvelId, memberId, from, to).stream()
                .map(ContributionResponse::from).toList();
    }

    @GET
    @Path("/{contributionId}")
    public ContributionResponse get(@PathParam("stokvelId") Long stokvelId,
                                    @PathParam("contributionId") Long contributionId) {
        return ContributionResponse.from(contributionService.find(stokvelId, contributionId));
    }

    // POST .../contributions with {"memberId":3,"amount":500,"paymentMethod":"EFT"}
    @POST
    public Response record(@PathParam("stokvelId") Long stokvelId, @Valid @NotNull ContributionRequest request,
                           @Context UriInfo uriInfo) {
        ContributionResponse recorded = ContributionResponse.from(contributionService.record(stokvelId, request));
        URI location = uriInfo.getAbsolutePathBuilder().path(recorded.id().toString()).build();
        return Response.created(location).entity(recorded).build();
    }

    // DELETE .../contributions/7 -> for correcting a capture mistake
    @DELETE
    @Path("/{contributionId}")
    public Response delete(@PathParam("stokvelId") Long stokvelId, @PathParam("contributionId") Long contributionId) {
        contributionService.delete(stokvelId, contributionId);
        return Response.noContent().build();
    }
}
