package com.tahir.finance.auth.security;

import com.tahir.finance.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies the short-lived access token. Refresh tokens are opaque
 * random strings stored hashed in the database, never JWTs - a JWT you cannot
 * revoke is the wrong tool for a long-lived credential.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final java.time.Duration accessTtl;

    public JwtService(AppProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters. Set APP_JWT_SECRET to a long random value.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = properties.jwt().issuer();
        this.accessTtl = properties.jwt().accessTokenTtl();
    }

    public String issueAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id, or null when the token is missing, malformed or expired. */
    public UUID resolveUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public long accessTokenSeconds() {
        return accessTtl.toSeconds();
    }
}
