package com.stokvault.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Switches on JAX-RS (Jakarta RESTful Web Services) for this app.
 *
 * @ApplicationPath("/api") sets the URL prefix for every REST endpoint, so a
 * resource with @Path("/members") is served at /stokvault/api/members.
 *
 * The class body is empty on purpose: with no overrides, the server scans the
 * WAR and registers every @Path class it finds.
 */
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
}
