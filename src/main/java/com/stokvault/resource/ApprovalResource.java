package com.stokvault.resource;

import com.stokvault.dto.PayoutView;
import com.stokvault.service.PayoutService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The committee's approval queue across all their groups: GET /api/approvals
 */
@Path("/approvals")
@Produces(MediaType.APPLICATION_JSON)
public class ApprovalResource {

    @Inject
    private PayoutService payouts;

    @GET
    public List<PayoutView> queue() {
        return payouts.approvalQueue().stream().map(PayoutView::from).toList();
    }
}
