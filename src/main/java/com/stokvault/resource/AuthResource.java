package com.stokvault.resource;

import com.stokvault.dto.AuthRequests;
import com.stokvault.service.AuthService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Public authentication endpoints (no login needed; see web.xml).
 * Every other API call authenticates with HTTP Basic: phone number and password.
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private AuthService auth;

    /**
     * POST /api/auth/login-code {"phoneNumber":"082 123 4567"} -> 202.
     * Sends a one-time login code by SMS if the number is registered. The response is the same
     * either way, so this can't be used to find out who has an account.
     */
    @POST
    @Path("/login-code")
    public Response requestLoginCode(@Valid @NotNull AuthRequests.OtpRequest request) {
        auth.requestLoginCode(request.phoneNumber());
        return Response.accepted(Map.of("message", "If that number is registered, a login code is on its way")).build();
    }
}
