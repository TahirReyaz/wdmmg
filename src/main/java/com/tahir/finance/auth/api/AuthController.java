package com.tahir.finance.auth.api;

import com.tahir.finance.auth.security.CurrentUser;
import com.tahir.finance.auth.service.AuthService;
import com.tahir.finance.auth.service.LoginRateLimiter;
import com.tahir.finance.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;
    private final AppProperties properties;

    public AuthController(AuthService auth, LoginRateLimiter rateLimiter, AppProperties properties) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 @RequestHeader(value = "User-Agent", required = false) String agent,
                                                 HttpServletRequest http) {
        rateLimiter.check(request.email(), clientIp(http));
        AuthService.Issued issued = auth.register(request, agent);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.rawRefreshToken()).toString())
                .body(issued.response());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              @RequestHeader(value = "User-Agent", required = false) String agent,
                                              HttpServletRequest http) {
        String ip = clientIp(http);
        rateLimiter.check(request.email(), ip);

        AuthService.Issued issued = auth.login(request, agent);
        rateLimiter.reset(request.email(), ip);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.rawRefreshToken()).toString())
                .body(issued.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @RequestHeader(value = "User-Agent", required = false) String agent,
            HttpServletRequest http) {

        AuthService.Issued issued = auth.refresh(readRefreshCookie(http), agent);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.rawRefreshToken()).toString())
                .body(new AccessTokenResponse(
                        issued.response().accessToken(),
                        issued.response().expiresInSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        auth.logout(readRefreshCookie(http));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    /** Ends every session for the signed-in user, not only this browser. */
    @PostMapping("/logout-everywhere")
    public ResponseEntity<Void> logoutEverywhere() {
        auth.logoutEverywhere(CurrentUser.id());

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    // ---- cookie plumbing ----------------------------------------------------

    private ResponseCookie refreshCookie(String value) {
        return baseCookie(value).maxAge(properties.jwt().refreshTokenTtl()).build();
    }

    private ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(properties.cookie().name(), value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .path(properties.cookie().path())
                .sameSite(properties.cookie().sameSite());

        if (properties.cookie().domain() != null && !properties.cookie().domain().isBlank()) {
            builder.domain(properties.cookie().domain());
        }
        return builder;
    }

    /**
     * Read by hand rather than with {@code @CookieValue}, because the cookie
     * name lives in configuration and annotation attributes are resolved before
     * that binding happens.
     */
    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String wanted = properties.cookie().name();
        for (Cookie cookie : cookies) {
            if (wanted.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
