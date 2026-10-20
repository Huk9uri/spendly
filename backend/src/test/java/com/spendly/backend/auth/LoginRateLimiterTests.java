package com.spendly.backend.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTests {
    @Test
    void limitsPairAndAllowsItAgainAtWindowExpiry() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 5; i++) { limiter.acquire("ip", "user@example.invalid"); }
        assertEquals(300, assertThrows(LoginRateLimiter.LimitExceeded.class,
                () -> limiter.acquire("ip", "user@example.invalid")).getRetryAfter());
        limiter.acquire("other-ip", "user@example.invalid");
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertDoesNotThrow(() -> limiter.acquire("ip", "user@example.invalid"));
    }

    @Test
    void limitsIpEvenWhenEmailChanges() {
        LoginRateLimiter limiter = new LoginRateLimiter(new MutableClock());
        for (int i = 0; i < 20; i++) { limiter.acquire("ip", i + "@example.invalid"); }
        assertThrows(LoginRateLimiter.LimitExceeded.class, () -> limiter.acquire("ip", "new@example.invalid"));
        assertDoesNotThrow(() -> limiter.acquire("other-ip", "new@example.invalid"));
    }

    @Test
    void boundsStorageAndReleasesExpiredEntries() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < 5_000; i++) { limiter.acquire("ip-" + i, "user@example.invalid"); }
        assertThrows(LoginRateLimiter.LimitExceeded.class, () -> limiter.acquire("new-ip", "user@example.invalid"));
        clock.now = clock.now.plusSeconds(300);
        assertDoesNotThrow(() -> limiter.acquire("new-ip", "user@example.invalid"));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
