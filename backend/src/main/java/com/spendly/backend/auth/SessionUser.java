package com.spendly.backend.auth;

import com.spendly.backend.user.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class SessionUser implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;
    private final UUID id;
    private final String email;
    private transient String passwordHash;

    public SessionUser(User user) {
        id = user.getId();
        email = user.getEmail();
        passwordHash = user.getPasswordHash();
    }

    public UUID getId() { return id; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public void eraseCredentials() { passwordHash = null; }
    @Override public String toString() { return "SessionUser[REDACTED]"; }
    public UserResponse response() { return new UserResponse(id, email); }
}
