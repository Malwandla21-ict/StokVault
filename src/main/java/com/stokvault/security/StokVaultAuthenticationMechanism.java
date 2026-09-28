package com.stokvault.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AutoApplySession;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decides how each HTTP request gets authenticated (Jakarta Security HttpAuthenticationMechanism).
 *
 *  - Web pages (/app/...): a login form. The LoginBean hands the typed credentials to this class
 *    via SecurityContext.authenticate(); on success the login is kept in the HTTP session
 *    (@AutoApplySession). Unauthenticated visitors are redirected to the login page.
 *  - REST API (/api/...): each request carries HTTP Basic credentials (phone:password), which
 *    suits scripts and mobile clients. Failures get a JSON 401 instead of a redirect.
 *
 * Which URLs need a login is declared in web.xml (security-constraint).
 */
@ApplicationScoped
@AutoApplySession
public class StokVaultAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                HttpMessageContext context) {
        boolean api = request.getRequestURI().startsWith(request.getContextPath() + "/api/");

        Credential credential = null;
        if (context.isAuthenticationRequest() && context.getAuthParameters().getCredential() != null) {
            credential = context.getAuthParameters().getCredential(); // from the login page
        } else {
            credential = basicCredential(request.getHeader("Authorization"));
        }

        if (credential != null) {
            CredentialValidationResult result = identityStoreHandler.validate(credential);
            if (result.getStatus() == CredentialValidationResult.Status.VALID) {
                return context.notifyContainerAboutLogin(result);
            }
            if (context.isAuthenticationRequest()) {
                return AuthenticationStatus.SEND_FAILURE; // the login page shows the error
            }
            return unauthorized(response, "Incorrect phone number or password, or the account is temporarily locked");
        }

        if (context.isProtected()) {
            if (api) {
                return unauthorized(response, "Authentication required: send HTTP Basic credentials (phone number and password)");
            }
            String target = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            return context.redirect(request.getContextPath() + "/login.xhtml?next="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8));
        }
        return context.doNothing();
    }

    /**
     * "Authorization: Basic base64(phone:password)" or, for one-time SMS codes,
     * "Authorization: OTP base64(phone:code)" (e.g. a new member setting their first password).
     */
    private static Credential basicCredential(String header) {
        if (header == null) {
            return null;
        }
        boolean otp = header.regionMatches(true, 0, "OTP ", 0, 4);
        if (!otp && !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String encoded = header.substring(otp ? 4 : 6).trim();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return null;
            }
            String phone = decoded.substring(0, colon);
            String secret = decoded.substring(colon + 1);
            return otp ? new OtpCredential(phone, secret) : new UsernamePasswordCredential(phone, secret);
        } catch (IllegalArgumentException e) {
            return null; // not valid Base64
        }
    }

    private static AuthenticationStatus unauthorized(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"StokVault\"");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(Json.createObjectBuilder()
                    .add("status", 401)
                    .add("error", "Unauthorized")
                    .add("message", message)
                    .add("details", Json.createArrayBuilder())
                    .build().toString());
        } catch (IOException e) {
            // the client went away; nothing more to do
        }
        return AuthenticationStatus.SEND_FAILURE;
    }
}
