package com.stokvault.resource;

import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.OptionalReason;
import com.stokvault.dto.PayoutRunRequest;
import com.stokvault.dto.PayoutView;
import com.stokvault.dto.ReasonRequest;
import com.stokvault.service.GroupService;
import com.stokvault.service.PayoutService;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payouts: /api/groups/{groupId}/payouts. See PayoutService for the full lifecycle.
 */
@Path("/groups/{groupId}/payouts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PayoutResource {

    @Inject
    private PayoutService payouts;

    @Inject
    private GroupService groups;

    @GET
    public List<PayoutView> list(@PathParam("groupId") UUID groupId, @QueryParam("status") PayoutStatus status) {
        return payouts.list(groupId, status).stream().map(PayoutView::from).toList();
    }

    @GET
    @Path("/{payoutId}")
    public PayoutView get(@PathParam("groupId") UUID groupId, @PathParam("payoutId") UUID payoutId) {
        return PayoutView.from(payouts.find(groups.find(groupId), payoutId));
    }

    /**
     * Initiate Payout (treasurer). The body depends on the group type, e.g. {} for the current
     * rotation cycle, or {"beneficiaryMemberId":"..."} for a burial claim. Returns the scheduled
     * payouts; the eligibility check starts automatically in the background.
     */
    @POST
    @Path("/run")
    public Response run(@PathParam("groupId") UUID groupId, @Valid PayoutRunRequest request) {
        PayoutRunRequest body = request != null ? request : new PayoutRunRequest(null, null, null, null, null);
        List<PayoutView> scheduled = payouts.run(groupId, body).stream().map(PayoutView::from).toList();
        return Response.status(Response.Status.CREATED).entity(scheduled).build();
    }

    /** Re-run the automated eligibility check now. 202 Accepted: it runs as a background batch job. */
    @POST
    @Path("/eligibility-check")
    public Response checkEligibility(@PathParam("groupId") UUID groupId) {
        long executionId = payouts.startEligibilityCheck(groupId);
        return Response.accepted(Map.of("executionId", executionId)).build();
    }

    @POST
    @Path("/{payoutId}/confirm")
    public PayoutView confirm(@PathParam("groupId") UUID groupId, @PathParam("payoutId") UUID payoutId) {
        return PayoutView.from(payouts.confirm(groupId, payoutId));
    }

    @POST
    @Path("/{payoutId}/approve")
    public PayoutView approve(@PathParam("groupId") UUID groupId, @PathParam("payoutId") UUID payoutId) {
        return PayoutView.from(payouts.approve(groupId, payoutId));
    }

    // {"reason":"Member paid in cash at the meeting; the treasurer will capture it"}
    @POST
    @Path("/{payoutId}/override")
    public PayoutView override(@PathParam("groupId") UUID groupId, @PathParam("payoutId") UUID payoutId,
                               @Valid @NotNull ReasonRequest request) {
        return PayoutView.from(payouts.override(groupId, payoutId, request.reason()));
    }

    @POST
    @Path("/{payoutId}/pay")
    public PayoutView pay(@PathParam("groupId") UUID groupId, @PathParam("payoutId") UUID payoutId) {
        return PayoutView.from(payouts.pay(groupId, payoutId));
    }

    @POST
    @Path("/{payoutId}/cancel")
    public PayoutView cancel(@PathParam("groupId") UUID groupId, @PathParam("payoutId") UUID payoutId,
                             @Valid OptionalReason body) {
        return PayoutView.from(payouts.cancel(groupId, payoutId, OptionalReason.of(body)));
    }
}
