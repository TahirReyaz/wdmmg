package com.tahir.finance.auth.service;

import com.tahir.finance.auth.api.AuthResponse;
import com.tahir.finance.auth.api.LoginRequest;
import com.tahir.finance.auth.api.RegisterRequest;
import com.tahir.finance.auth.domain.RefreshToken;
import com.tahir.finance.auth.domain.RefreshTokenRepository;
import com.tahir.finance.auth.security.JwtService;
import com.tahir.finance.category.service.CategoryService;
import com.tahir.finance.common.Uuid7;
import com.tahir.finance.common.error.ApiException;
import com.tahir.finance.config.AppProperties;
import com.tahir.finance.user.api.UserResponse;
import com.tahir.finance.user.domain.User;
import com.tahir.finance.user.domain.UserCredential;
import com.tahir.finance.user.domain.UserCredentialRepository;
import com.tahir.finance.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A dummy Argon2 hash. Verifying against it when the email is unknown keeps
     * login timing the same whether or not the account exists.
     */
    private final String decoyHash;

    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CategoryService categories;
    private final AppProperties properties;

    public AuthService(UserRepository users,
                       UserCredentialRepository credentials,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CategoryService categories,
                       AppProperties properties) {
        this.users = users;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.categories = categories;
        this.properties = properties;
        this.decoyHash = passwordEncoder.encode("a-password-nobody-will-ever-use");
    }

    // ---- registration -------------------------------------------------------

    @Transactional
    public Issued register(RegisterRequest request, String userAgent) {
        String email = request.email().trim().toLowerCase();

        if (users.emailTaken(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }

        User user = new User();
        user.setId(Uuid7.generate());
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        if (request.baseCurrency() != null && request.baseCurrency().length() == 3) {
            user.setBaseCurrency(request.baseCurrency().toUpperCase());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone());
        }
        users.save(user);

        UserCredential credential = new UserCredential();
        credential.setUserId(user.getId());
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credentials.save(credential);

        categories.seedDefaultsFor(user.getId());

        return issueFor(user, UUID.randomUUID(), userAgent);
    }

    // ---- login --------------------------------------------------------------

    @Transactional
    public Issued login(LoginRequest request, String userAgent) {
        Optional<User> maybeUser = users.findActiveByEmail(request.email().trim());

        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(request.password(), decoyHash);   // constant-ish time
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Email or password is incorrect.");
        }

        User user = maybeUser.get();
        String hash = credentials.findById(user.getId())
                .map(UserCredential::getPasswordHash)
                .orElse(decoyHash);

        if (!passwordEncoder.matches(request.password(), hash)) {
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Email or password is incorrect.");
        }

        return issueFor(user, UUID.randomUUID(), userAgent);
    }

    // ---- refresh ------------------------------------------------------------

    @Transactional
    public Issued refresh(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("NO_REFRESH_TOKEN", "No refresh token was supplied.");
        }

        RefreshToken stored = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN",
                        "This session is no longer valid. Please sign in again."));

        Instant now = Instant.now();

        // Reuse of an already-rotated token means the cookie leaked. Kill the
        // whole family rather than just this one token.
        if (!stored.isUsable(now)) {
            refreshTokens.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} - family {} revoked",
                    stored.getUserId(), stored.getFamilyId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED",
                    "This session was ended for security reasons. Please sign in again.");
        }

        User user = users.findById(stored.getUserId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.unauthorized("ACCOUNT_GONE", "This account no longer exists."));

        Issued issued = issueFor(user, stored.getFamilyId(), userAgent);

        stored.setRevokedAt(now);
        stored.setReplacedBy(issued.refreshTokenId());
        refreshTokens.save(stored);

        return issued;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(rawToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    // ---- internals ----------------------------------------------------------

    private Issued issueFor(User user, UUID familyId, String userAgent) {
        String rawRefresh = randomToken();

        RefreshToken token = new RefreshToken();
        token.setId(Uuid7.generate());
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(sha256(rawRefresh));
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 250)));
        refreshTokens.save(token);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());

        return new Issued(
                new AuthResponse(UserResponse.from(user), accessToken, jwtService.accessTokenSeconds()),
                rawRefresh,
                token.getId());
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", ex);
        }
    }

    /** The response body plus the raw refresh token, which only the controller may see. */
    public record Issued(AuthResponse response, String rawRefreshToken, UUID refreshTokenId) {
    }
}
