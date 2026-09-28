package com.stokvault.resource;

import com.stokvault.dto.CycleGrid;
import com.stokvault.dto.CycleView;
import com.stokvault.service.CycleService;
import com.stokvault.service.LedgerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contribution cycles: /api/groups/{groupId}/cycles
 */
@Path("/groups/{groupId}/cycles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CycleResource {

    @Inject
    private CycleService cycles;

    @Inject
    private LedgerService ledger;

    @GET
    public List<CycleView> list(@PathParam("groupId") UUID groupId) {
        return cycles.list(groupId);
    }

    // POST .../cycles (treasurer) opens the next cycle; ?dueDate=2026-10-01 overrides the calculated due date
    @POST
    public Response open(@PathParam("groupId") UUID groupId, @QueryParam("dueDate") LocalDate dueDate) {
        return Response.status(Response.Status.CREATED).entity(ledger.view(cycles.open(groupId, dueDate))).build();
    }

    /** Who has paid, who is outstanding (treasurer and committee). */
    @GET
    @Path("/{cycleId}/grid")
    public CycleGrid grid(@PathParam("groupId") UUID groupId, @PathParam("cycleId") UUID cycleId) {
        return cycles.grid(groupId, cycleId);
    }

    @POST
    @Path("/{cycleId}/close")
    public CycleView close(@PathParam("groupId") UUID groupId, @PathParam("cycleId") UUID cycleId) {
        return ledger.view(cycles.close(groupId, cycleId));
    }

    @POST
    @Path("/{cycleId}/reconcile")
    public CycleView reconcile(@PathParam("groupId") UUID groupId, @PathParam("cycleId") UUID cycleId) {
        return ledger.view(cycles.reconcile(groupId, cycleId));
    }
}
