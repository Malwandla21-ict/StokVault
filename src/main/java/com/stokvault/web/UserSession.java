package com.stokvault.web;

import com.stokvault.dto.MemberView;
import com.stokvault.security.Roles;
import com.stokvault.service.MemberService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.SecurityContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;

/**
 * The logged-in user, available to every page as #{user}.
 * (Jakarta Security SESSION state lives in @AutoApplySession; this bean just caches display details.)
 */
// @SessionScoped: one instance per browser session, kept between requests (so it must be Serializable)
@Named("user")
@SessionScoped
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private MemberService members;

    private MemberView me;

    public MemberView getMe() {
        if (me == null && isLoggedIn()) {
            me = MemberView.from(members.me());
        }
        return me;
    }

    /** Called after the user changes their details. */
    public void refresh() {
        me = null;
    }

    public boolean isLoggedIn() {
        return securityContext.getCallerPrincipal() != null;
    }

    public boolean isAdmin() {
        return securityContext.isCallerInRole(Roles.ADMIN);
    }

    public boolean isCommittee() {
        return securityContext.isCallerInRole(Roles.COMMITTEE);
    }

    public boolean isTreasurer() {
        return securityContext.isCallerInRole(Roles.TREASURER);
    }

    public String getFirstName() {
        MemberView m = getMe();
        return m == null ? "" : m.fullName().split(" ")[0];
    }

    public String logout() throws ServletException {
        ExternalContext external = FacesContext.getCurrentInstance().getExternalContext();
        ((HttpServletRequest) external.getRequest()).logout();
        external.invalidateSession();
        return "/login.xhtml?faces-redirect=true&loggedOut=true";
    }
}
