package com.stokvault.resource;

import com.stokvault.service.HealthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Proves the app is deployed and can reach the database.
 * GET http://localhost:8081/stokvault/api/health -> {"status":"ok","database":"up"}
 * If the database is unreachable: 503 Service Unavailable with {"status":"degraded","database":"down"}
 */
// @Path: the URL this class answers, relative to @ApplicationPath("/api")
@Path("/health")
public class HealthCheckResource {

    @Inject
    private HealthService healthService;

    // @GET: this method handles HTTP GET requests
    @GET
    // @Produces: the response body is JSON. JSON-B (built into Jakarta EE)
    // converts the returned Java object to JSON for us.
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        if (healthService.isDatabaseUp()) {
            return Response.ok(Map.of("status", "ok", "database", "up")).build();
        }
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("status", "degraded", "database", "down"))
                .build();
    }
}
