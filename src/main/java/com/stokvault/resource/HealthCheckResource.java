package com.stokvault.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * A tiny endpoint to prove the app deployed and JAX-RS is working.
 * GET http://localhost:8080/stokvault/api/health  ->  {"status":"ok"}
 */
// @Path: the URL this class answers, relative to @ApplicationPath("/api")
@Path("/health")
public class HealthCheckResource {

    // @GET: this method handles HTTP GET requests
    @GET
    // @Produces: the response body is JSON. JSON-B (built into Jakarta EE)
    // converts the returned Java object to JSON for us.
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
