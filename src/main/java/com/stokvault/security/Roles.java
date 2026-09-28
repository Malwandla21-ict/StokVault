package com.stokvault.security;

/**
 * The four roles from the SDD (7.2). A logged-in user's caller groups are computed at login:
 *  - everyone gets MEMBER,
 *  - TREASURER / COMMITTEE if they hold that role in at least one group,
 *  - ADMIN for Coop Office administrators.
 * These coarse roles are checked with @RolesAllowed. Whether someone is treasurer of THIS group
 * is checked per request by AccessControl, because roles are scoped per group.
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String TREASURER = "TREASURER";
    public static final String COMMITTEE = "COMMITTEE";
    public static final String MEMBER = "MEMBER";

    private Roles() {
    }
}
