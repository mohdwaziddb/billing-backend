package com.billing.security;

import com.billing.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account brute-force lockout (IP rate limit ke upar ek aur parat).
 * IP badal-badal kar (botnet/proxy) ek hi account ka password guess karna
 * yahan rukta hai: 5 galat password -> account 15 minute ke liye locked.
 *
 * In-memory: single instance ke liye kaafi. Multi-instance par Redis chahiye.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MILLIS = 15 * 60_000L;
    static final long LOCK_MILLIS = 15 * 60_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    private static final class Attempts {
        long windowStart;
        int failures;
        long lockedUntil;
    }

    public static String key(String scope, String identifier) {
        String safeScope = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        String safeIdentifier = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return safeScope + "|" + safeIdentifier;
    }

    public void assertNotLocked(String key) {
        Attempts entry = attempts.get(key);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.lockedUntil > System.currentTimeMillis()) {
                throw new UnauthorizedException("Too many failed login attempts. Please try again in 15 minutes.");
            }
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        cleanupIfLarge(now);
        Attempts entry = attempts.computeIfAbsent(key, ignored -> new Attempts());
        synchronized (entry) {
            if (now - entry.windowStart >= WINDOW_MILLIS) {
                entry.windowStart = now;
                entry.failures = 0;
            }
            entry.failures++;
            if (entry.failures >= MAX_FAILURES) {
                entry.lockedUntil = now + LOCK_MILLIS;
                entry.failures = 0;
                entry.windowStart = now;
            }
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    // Map ko unbounded badhne se roko (har galat identifier ek entry banata hai).
    private void cleanupIfLarge(long now) {
        if (attempts.size() < CLEANUP_THRESHOLD) {
            return;
        }
        attempts.entrySet().removeIf(entry -> {
            Attempts value = entry.getValue();
            synchronized (value) {
                return value.lockedUntil <= now && now - value.windowStart >= WINDOW_MILLIS;
            }
        });
    }
}
