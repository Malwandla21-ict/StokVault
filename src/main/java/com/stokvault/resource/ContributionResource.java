package com.stokvault.resource;

import com.stokvault.domain.VerificationStatus;
import com.stokvault.dto.ContributionRequest;
import com.stokvault.dto.ContributionView;
import com.stokvault.dto.VerificationDecision;
import com.stokvault.exception.ResourceNotFoundException;
import com.stokvault.service.ContributionService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
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

import java.util.List;
import java.util.UUID;

/**
 * Contributions: /api/groups/{groupId}/contributions
 */
@Path("/groups/{groupId}/contributions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContributionResource {

    @Inject
    private ContributionService contributions;

    // ?cycleId=...&memberId=...&status=PENDING_REVIEW (all optional). Plain members only ever get their own.
    @GET
    public List<ContributionView> list(@PathParam("groupId") UUID groupId, @QueryParam("cycleId") UUID cycleId,
                                       @QueryParam("memberId") UUID memberId, @QueryParam("status") VerificationStatus status) {
        return contributions.list(groupId, cycleId, memberId, status).stream().map(ContributionView::from).toList();
    }

    /**
     * 201 Created for a new contribution; 200 OK with the existing one when the same
     * (member, cycle, payment reference) was already recorded (idempotent retries are safe).
     */
    @POST
    public Response record(@PathParam("groupId") UUID groupId, @Valid @NotNull ContributionRequest request, @Context UriInfo uri) {
        ContributionService.Recorded recorded = contributions.record(groupId, request);
        ContributionView view = ContributionView.from(recorded.contribution());
        if (!recorded.created()) {
            return Response.ok(view).build();
        }
        return Response.created(uri.getAbsolutePathBuilder().path(view.id().toString()).build()).entity(view).build();
    }

    @GET
    @Path("/{contributionId}")
    public ContributionView get(@PathParam("groupId") UUID groupId, @PathParam("contributionId") UUID contributionId) {
        // Reuse the list's visibility rules: plain members can only fetch their own
        return contributions.list(groupId, null, null, null).stream()
                .filter(c -> c.getId().equals(contributionId))
                .findFirst()
                .map(ContributionView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution " + contributionId + " not found"));
    }

    // {"decision":"VERIFIED"} or {"decision":"REJECTED","note":"No such deposit on the statement"}
    @POST
    @Path("/{contributionId}/verify")
    public ContributionView verify(@PathParam("groupId") UUID groupId, @PathParam("contributionId") UUID contributionId,
                                   @Valid @NotNull VerificationDecision decision) {
        return ContributionView.from(contributions.verify(groupId, contributionId, decision));
    }
}
