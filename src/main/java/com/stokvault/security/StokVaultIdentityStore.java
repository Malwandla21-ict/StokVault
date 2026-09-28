package com.stokvault.security;

import com.stokvault.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;

import java.util.Optional;

/**
 * The custom, JPA-backed IdentityStore from the SDD (4.6): checks a phone number plus password
 * or one-time code against the members table and returns the caller's roles.
 *
 * Jakarta Security finds this automatically (it's a CDI bean implementing IdentityStore) and
 * the authentication mechanism calls it through IdentityStoreHandler.
 */
@ApplicationScoped
public class StokVaultIdentityStore implements IdentityStore {

    @Inject
    private AuthService authService;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        Optional<AuthService.Login> login;
        if (credential instanceof UsernamePasswordCredential password) {
            login = authService.checkPassword(password.getCaller(), password.getPasswordAsString());
        } else if (credential instanceof OtpCredential otp) {
            login = authService.checkOtp(otp.phoneNumber(), otp.code());
        } else {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        // The caller principal's name is the member's UUID; the groups become the caller's roles
        return login
                .map(l -> new CredentialValidationResult(l.memberId().toString(), l.roles()))
                .orElse(CredentialValidationResult.INVALID_RESULT);
    }
}
