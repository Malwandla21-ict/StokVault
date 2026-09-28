package com.stokvault.resource;

import jakarta.annotation.Resource;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * GET /api/health (no login needed): {"status":"ok","database":"up"}, or 503 when the database
 * can't be reached.
 */
@Path("/health")
public class HealthResource {

    // The same JNDI data source JPA uses (configured in Payara)
    @Resource(lookup = "jdbc/StokVaultDS")
    private DataSource dataSource;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(3)) {
                return Response.ok(Map.of("status", "ok", "database", "up")).build();
            }
        } catch (SQLException e) {
            // fall through to 503
        }
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("status", "degraded", "database", "down")).build();
    }
}
