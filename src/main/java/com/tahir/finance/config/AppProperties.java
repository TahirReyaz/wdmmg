package com.tahir.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cookie cookie, Cors cors, RateLimit rateLimit) {

    /** {@code secret} must be at least 32 characters and comes from APP_JWT_SECRET. */
    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cookie(String name, String path, boolean secure, String sameSite, String domain) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record RateLimit(int loginAttempts, Duration loginWindow, int writesPerMinute) {
    }
}
