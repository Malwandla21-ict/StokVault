package com.stokvault.web;

import com.stokvault.security.OtpCredential;
import com.stokvault.service.AuthService;
import com.stokvault.service.MemberService;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;

/**
 * The login page: phone number + password, or phone number + a one-time SMS code (SDD 7.1).
 * The credentials are handed to Jakarta Security with SecurityContext.authenticate(); the
 * StokVaultAuthenticationMechanism and identity store do the actual checking.
 */
// @ViewScoped: lives as long as the user stays on this page (keeps "code sent" between clicks)
@Named
@ViewScoped
public class LoginBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private AuthService authService;

    @Inject
    private MemberService members;

    private String phoneNumber;
    private String password;
    private String code;
    private boolean codeSent;
    private String next;

    public void login() throws IOException {
        authenticate(new UsernamePasswordCredential(phoneNumber == null ? "" : phoneNumber, password == null ? "" : password));
    }

    public void sendCode() {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            Ui.error("Enter your phone number first");
            return;
        }
        authService.requestLoginCode(phoneNumber);
        codeSent = true;
        Ui.info("If that number is registered, a 6-digit code is on its way by SMS. It expires in 5 minutes.");
    }

    public void loginWithCode() throws IOException {
        authenticate(new OtpCredential(phoneNumber == null ? "" : phoneNumber, code == null ? "" : code));
    }

    public void useCode() {
        codeSent = false;
        code = null;
        sendCode();
    }

    private void authenticate(Credential credential) throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        ExternalContext external = context.getExternalContext();
        AuthenticationStatus status = securityContext.authenticate(
                (HttpServletRequest) external.getRequest(),
                (HttpServletResponse) external.getResponse(),
                AuthenticationParameters.withParams().credential(credential).newAuthentication(true));

        switch (status) {
            case SUCCESS -> {
                // New members arrive with an SMS code and must choose a password first
                boolean needsPassword = !members.me().isPasswordSet();
                String target = needsPassword ? "/app/account.xhtml?welcome=true" : safeNext();
                external.redirect(external.getRequestContextPath() + target);
                context.responseComplete();
            }
            case SEND_CONTINUE -> context.responseComplete();
            default -> Ui.error("That didn't work. Check your phone number and "
                    + (credential instanceof OtpCredential ? "code (codes expire after 5 minutes)" : "password")
                    + ". After 5 failed attempts the account is locked for 15 minutes.");
        }
    }

    /** Only redirect back into the app itself (never to another site). */
    private String safeNext() {
        String contextPath = FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath();
        if (next != null && next.startsWith(contextPath + "/app/") && !next.contains("//")) {
            return next.substring(contextPath.length());
        }
        return "/app/index.xhtml";
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isCodeSent() {
        return codeSent;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }
}
