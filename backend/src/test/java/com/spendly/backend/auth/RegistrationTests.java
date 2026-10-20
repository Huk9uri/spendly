package com.spendly.backend.auth;

import com.spendly.backend.common.ApiException;
import com.spendly.backend.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RegistrationTests {
    @Autowired private AuthService service;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    void persistsNormalizedEmailAndHashToPostgres() {
        String email = UUID.randomUUID() + "@example.invalid";
        String password = "  test-password-123  ";
        UserResponse response = service.register(new CredentialsRequest("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", password));
        var stored = users.findByEmail(email).orElseThrow();
        assertEquals(response.id(), stored.getId());
        assertEquals(email, response.email());
        assertNotNull(stored.getCreatedAt());
        assertTrue(passwords.matches(password, stored.getPasswordHash()));
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        String email = UUID.randomUUID() + "@example.invalid";
        service.register(new CredentialsRequest(email, "test-password-123"));
        ApiException error = assertThrows(ApiException.class,
                () -> service.register(new CredentialsRequest(email.toUpperCase(java.util.Locale.ROOT), "test-password-456")));
        assertEquals(409, error.getStatus().value());
        assertEquals("EMAIL_ALREADY_EXISTS", error.getCode());
    }
}
