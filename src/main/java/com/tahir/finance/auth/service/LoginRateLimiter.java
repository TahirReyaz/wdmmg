package com.tahir.finance.auth.service;

import com.tahir.finance.common.error.ApiException;
import com.tahir.finance.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window limiter for the credential endpoints.
 * <p>
 * Good enough for a single replica. With more than one instance this moves to
 * Redis, which is why the check is behind a method rather than inlined.
 */
@Component
public class LoginRateLimiter {

    private record Window(Instant resetAt, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimiter(AppProperties properties) {
        this.maxAttempts = properties.rateLimit().loginAttempts();
        this.window = properties.rateLimit().loginWindow();
    }

    public void check(String email, String clientIp) {
        String key = (email == null ? "" : email.toLowerCase()) + "|" + clientIp;
        Instant now = Instant.now();

        sweep(now);

        Window current = windows.compute(key, (k, existing) ->
                (existing == null || existing.resetAt().isBefore(now))
                        ? new Window(now.plus(window), new AtomicInteger(0))
                        : existing);

        if (current.count().incrementAndGet() > maxAttempts) {
            long retryAfter = Math.max(1, Duration.between(now, current.resetAt()).toSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many attempts. Try again in " + retryAfter + " seconds.",
                    Map.of("retryAfterSeconds", retryAfter));
        }
    }

    /** A successful sign-in clears the counter for that key. */
    public void reset(String email, String clientIp) {
        windows.remove((email == null ? "" : email.toLowerCase()) + "|" + clientIp);
    }

    private void sweep(Instant now) {
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf((Entry<String, Window> entry) -> entry.getValue().resetAt().isBefore(now));
        }
    }
}
