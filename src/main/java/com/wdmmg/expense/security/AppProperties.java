package com.wdmmg.expense.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Admin admin, String zone) {
    public record Jwt(String secret, long expirationHours) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Admin(String email, String password, String name) {}
}
