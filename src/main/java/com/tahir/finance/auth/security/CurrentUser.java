package com.tahir.finance.auth.security;

import com.tahir.finance.common.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * The authenticated user id, read from the SecurityContext rather than from any
 * path variable. Ownership is never inferred from the URL.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw ApiException.unauthorized("NOT_AUTHENTICATED", "This endpoint requires a valid access token.");
        }
        return userId;
    }
}
