package com.spendly.backend.auth;

import com.spendly.backend.user.User;
import com.spendly.backend.user.UserRepository;
import com.spendly.backend.common.ApiErrorWriter;
import com.spendly.backend.common.ApiExceptionHandler;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthService.class, LoginRateLimiter.class, ApiErrorWriter.class, ApiExceptionHandler.class})
@ActiveProfiles("local")
class AuthTests {
    private static final String PASSWORD = "  test-password-123  ";
    private static final AtomicInteger IPS = new AtomicInteger();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    private String email;
    private String ip;

    @BeforeEach
    void uniqueIdentity() {
        email = UUID.randomUUID() + "@example.invalid";
        ip = "192.0.2." + IPS.incrementAndGet();
        Map<String, User> stored = new HashMap<>();
        when(users.findByEmail(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(users.existsByEmail(anyString())).thenAnswer(call -> stored.containsKey(call.getArgument(0)));
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            stored.put(user.getEmail(), user);
            return user;
        });
    }

    @Test
    void registersNormalizedEmailAndHashesPasswordWithoutLoggingIn() throws Exception {
        Token token = token(null);
        MvcResult registration = register(token, "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", PASSWORD, 201);
        User stored = users.findByEmail(email).orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(stored.getPasswordHash().startsWith("{pbkdf2@SpringSecurity_v5_8}"));
        assertTrue(passwords.matches(PASSWORD, stored.getPasswordHash()));
        assertFalse(passwords.matches(PASSWORD.strip(), stored.getPasswordHash()));
        assertSafe(registration, stored.getPasswordHash());
        mvc.perform(get("/api/me").session(token.session())).andExpect(status().isUnauthorized());
        register(token, email.toUpperCase(java.util.Locale.ROOT), PASSWORD, 409);
    }

    @Test
    void validatesInputWithoutEchoingSensitiveValues() throws Exception {
        Token token = token(null);
        register(token, "invalid-email", PASSWORD, 400);
        register(token, email, "short", 400);
        register(token, email, "x".repeat(129), 400);
        MvcResult malformed = mvc.perform(post("/api/auth/register").session(token.session())
                        .header(token.header(), token.value()).contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-secret-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andReturn();
        assertFalse(malformed.getResponse().getContentAsString().contains("invalid-secret-json"));
        mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(), token.value())
                        .contentType(MediaType.TEXT_PLAIN).content("invalid-secret-body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        assertEquals("CredentialsRequest[REDACTED]", new CredentialsRequest(email, PASSWORD).toString());
    }

    @Test
    void acceptsPasswordLengthBoundariesAndMapsConcurrentDuplicate() throws Exception {
        Token token = token(null);
        register(token, "min-" + email, "x".repeat(12), 201);
        register(token, "max-" + email, "x".repeat(128), 201);
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("redacted",
                new ConstraintViolationException("redacted", new SQLException("redacted", "23505"), "uk_users_email")));
        MvcResult duplicate = register(token, email, PASSWORD, 409);
        assertTrue(duplicate.getResponse().getContentAsString().contains("EMAIL_ALREADY_EXISTS"));
        assertFalse(duplicate.getResponse().getContentAsString().contains("uk_users_email"));
    }

    @Test
    void requiresCsrfForRegistrationLoginAndLogout() throws Exception {
        for (String path : new String[]{"register", "login", "logout"}) {
            mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(email, PASSWORD)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
        Token token = token(null);
        mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(), "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsInRotatesSessionAndCsrfThenLogsOut() throws Exception {
        Token before = token(null);
        register(before, email, PASSWORD, 201);
        String oldSessionId = before.session().getId();
        MvcResult login = login(before, email, PASSWORD, 200);
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotEquals(oldSessionId, session.getId());
        assertSafe(login, users.findByEmail(email).orElseThrow().getPasswordHash());
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNull(((SessionUser) context.getAuthentication().getPrincipal()).getPassword());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session).header(before.header(), before.value()))
                .andExpect(status().isForbidden());
        Token after = token(session);
        register(after, email, PASSWORD, 409);
        login(after, email, PASSWORD, 409);
        mvc.perform(post("/api/auth/logout").session(session).header(after.header(), after.value()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        Token anonymous = token(null);
        mvc.perform(post("/api/auth/logout").session(anonymous.session())
                        .header(anonymous.header(), anonymous.value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void hidesAccountExistenceAndLimitsRepeatedAttempts() throws Exception {
        Token token = token(null);
        register(token, email, PASSWORD, 201);
        MvcResult known = login(token, email, "wrong-password-123", 401);
        MvcResult unknown = login(token, "missing-" + email, "wrong-password-123", 401);
        assertEquals(known.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        for (int i = 0; i < 4; i++) { login(token, email, "wrong-password-123", 401); }
        MvcResult limited = login(token, email, PASSWORD, 429);
        assertNotNull(limited.getResponse().getHeader("Retry-After"));
        assertTrue(Long.parseLong(limited.getResponse().getHeader("Retry-After")) > 0);
        mvc.perform(get("/api/me").session(token.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesCrossOriginCredentialRequests() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private Token token(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) { request.session(session); }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        return new Token((MockHttpSession) result.getRequest().getSession(false),
                json.get("token").asText(), json.get("headerName").asText());
    }

    private MvcResult register(Token token, String email, String password, int status) throws Exception {
        return mvc.perform(post("/api/auth/register").session(token.session()).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)))
                .andExpect(status().is(status)).andReturn();
    }

    private MvcResult login(Token token, String email, String password, int status) throws Exception {
        return mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(), token.value())
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email, password)))
                .andExpect(status().is(status)).andReturn();
    }

    private String credentials(String email, String password) {
        return mapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private void assertSafe(MvcResult result, String hash) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains(hash));
        assertEquals(2, mapper.readTree(body).size());
    }

    private record Token(MockHttpSession session, String value, String header) { }
}
