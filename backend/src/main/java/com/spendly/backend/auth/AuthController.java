package com.spendly.backend.auth;

import com.spendly.backend.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService service;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final LoginRateLimiter limiter;

    public AuthController(AuthService service, AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessions, SecurityContextRepository contexts, LoginRateLimiter limiter) {
        this.service = service;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.contexts = contexts;
        this.limiter = limiter;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(), token.getHeaderName()); }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody CredentialsRequest credentials) {
        requireAnonymous();
        return service.register(credentials);
    }

    @PostMapping("/api/auth/login")
    public UserResponse login(@Valid @RequestBody CredentialsRequest credentials,
            HttpServletRequest request, HttpServletResponse response) {
        requireAnonymous();
        limiter.acquire(request.getRemoteAddr(), credentials.email());
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email(), credentials.password()));
        sessions.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ((SessionUser) authentication.getPrincipal()).response();
    }

    @GetMapping("/api/me")
    public UserResponse me(@AuthenticationPrincipal SessionUser user) { return user.response(); }

    private void requireAnonymous() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_AUTHENTICATED", "로그아웃 후 다시 시도해 주세요.");
        }
    }

    public record CsrfResponse(String token, String headerName) { }
}
