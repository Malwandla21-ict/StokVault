package com.stokvault.resource;

import com.stokvault.domain.PayoutStatus;
import com.stokvault.dto.NextPayoutResponse;
import com.stokvault.dto.PayoutRequest;
import com.stokvault.dto.PayoutResponse;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

/**
 * Money paid out of one stokvel: /api/stokvels/{stokvelId}/payouts.
 */
@Path("/stokvels/{stokvelId}/payouts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PayoutResource {

    @Inject
    private PayoutService payoutService;

    // GET .../payouts?status=SCHEDULED (status optional)
    @GET
    public List<PayoutResponse> list(@PathParam("stokvelId") Long stokvelId,
                                     @QueryParam("status") PayoutStatus status) {
        return payoutService.list(stokvelId, status).stream().map(PayoutResponse::from).toList();
    }

    // GET .../payouts/next -> whose turn it is (ROTATING stokvels only).
    // JAX-RS prefers this fixed path over the /{payoutId} template below.
    @GET
    @Path("/next")
    public NextPayoutResponse next(@PathParam("stokvelId") Long stokvelId) {
        return payoutService.next(stokvelId);
    }

    @GET
    @Path("/{payoutId}")
    public PayoutResponse get(@PathParam("stokvelId") Long stokvelId, @PathParam("payoutId") Long payoutId) {
        return PayoutResponse.from(payoutService.find(stokvelId, payoutId));
    }

    // POST .../payouts with {"memberId":3,"amount":2500,"payoutDate":"2026-09-30"} -> SCHEDULED
    @POST
    public Response schedule(@PathParam("stokvelId") Long stokvelId, @Valid @NotNull PayoutRequest request,
                             @Context UriInfo uriInfo) {
        PayoutResponse scheduled = PayoutResponse.from(payoutService.schedule(stokvelId, request));
        URI location = uriInfo.getAbsolutePathBuilder().path(scheduled.id().toString()).build();
        return Response.created(location).entity(scheduled).build();
    }

    // POST .../payouts/4/pay -> PAID, or 409 if the balance is too low
    @POST
    @Path("/{payoutId}/pay")
    public PayoutResponse pay(@PathParam("stokvelId") Long stokvelId, @PathParam("payoutId") Long payoutId) {
        return PayoutResponse.from(payoutService.pay(stokvelId, payoutId));
    }

    @POST
    @Path("/{payoutId}/cancel")
    public PayoutResponse cancel(@PathParam("stokvelId") Long stokvelId, @PathParam("payoutId") Long payoutId) {
        return PayoutResponse.from(payoutService.cancel(stokvelId, payoutId));
    }
}
