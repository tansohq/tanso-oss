package com.tansoflow.tansocore.auth;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.Collection;

@RequiredArgsConstructor
public class UserContextAuthentication implements Authentication {
    private final UserContext principal;
    private boolean authenticated = true;

    // TODO: reintroduce authorities later
    private final Collection<? extends GrantedAuthority> authorities;

    @Setter
    WebAuthenticationDetails details;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities; // or roles if needed later
    }

    @Override
    public Object getCredentials() {
        return principal.getApiKey();
    }

    @Override
    public Object getDetails() {
        return details;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return principal.getAccountId();
    }

}

