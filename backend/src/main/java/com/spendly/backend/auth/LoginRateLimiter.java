package com.spendly.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final int MAX_KEYS = 10_000;
    private final Clock clock;
    private final Map<Key, Bucket> buckets = new HashMap<>();

    public LoginRateLimiter() { this(Clock.systemUTC()); }
    LoginRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void acquire(String ip, String normalizedEmail) {
        long now = clock.millis();
        buckets.values().removeIf(bucket -> bucket.expiresAt <= now);
        Key ipKey = new Key(ip, null);
        Key pairKey = new Key(ip, normalizedEmail);
        Bucket ipBucket = buckets.get(ipKey);
        Bucket pairBucket = buckets.get(pairKey);
        long retryAt = 0;
        if (ipBucket != null && ipBucket.count >= 20) { retryAt = ipBucket.expiresAt; }
        if (pairBucket != null && pairBucket.count >= 5) { retryAt = Math.max(retryAt, pairBucket.expiresAt); }
        if (retryAt > 0) { throw new LimitExceeded(secondsUntil(now, retryAt)); }
        int newKeys = (ipBucket == null ? 1 : 0) + (pairBucket == null ? 1 : 0);
        if (buckets.size() + newKeys > MAX_KEYS) {
            long nextExpiry = buckets.values().stream().mapToLong(bucket -> bucket.expiresAt).min().orElse(now + WINDOW_MILLIS);
            throw new LimitExceeded(secondsUntil(now, nextExpiry));
        }
        buckets.computeIfAbsent(ipKey, key -> new Bucket(now + WINDOW_MILLIS)).count++;
        buckets.computeIfAbsent(pairKey, key -> new Bucket(now + WINDOW_MILLIS)).count++;
    }

    private long secondsUntil(long now, long expiry) { return Math.max(1, (expiry - now + 999) / 1000); }
    private record Key(String ip, String email) { }
    private static class Bucket {
        private final long expiresAt;
        private int count;
        Bucket(long expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class LimitExceeded extends RuntimeException {
        private final long retryAfter;
        LimitExceeded(long retryAfter) {
            super("Login attempt limit exceeded");
            this.retryAfter = retryAfter;
        }
        public long getRetryAfter() { return retryAfter; }
    }
}
