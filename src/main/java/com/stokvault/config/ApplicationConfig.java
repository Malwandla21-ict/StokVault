package com.stokvault.config;

import com.stokvault.security.Roles;
import jakarta.annotation.security.DeclareRoles;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Switches on Jakarta REST (JAX-RS). Every resource is served under /stokvault/api/...
 * The empty body means: find all @Path and @Provider classes in the WAR automatically.
 */
@ApplicationPath("/api")
// @DeclareRoles: the security roles the application uses (SDD 7.2). Payara maps each role to the
// caller group of the same name returned by StokVaultIdentityStore.
@DeclareRoles({Roles.ADMIN, Roles.TREASURER, Roles.COMMITTEE, Roles.MEMBER})
public class ApplicationConfig extends Application {
}
