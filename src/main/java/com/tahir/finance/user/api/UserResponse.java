package com.tahir.finance.user.api;

import com.tahir.finance.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String baseCurrency,
        String timezone,
        boolean emailVerified,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBaseCurrency(),
                user.getTimezone(),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt());
    }
}
