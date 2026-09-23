package com.tahir.finance.user.service;

import com.tahir.finance.common.error.ApiException;
import com.tahir.finance.user.api.UpdateProfileRequest;
import com.tahir.finance.user.api.UserResponse;
import com.tahir.finance.user.domain.User;
import com.tahir.finance.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse profile(UUID userId) {
        return UserResponse.from(activeOrThrow(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = activeOrThrow(userId);

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.baseCurrency() != null) {
            user.setBaseCurrency(request.baseCurrency().toUpperCase());
        }
        if (request.timezone() != null) {
            if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
                throw ApiException.unprocessable("BAD_TIMEZONE",
                        "'" + request.timezone() + "' is not a recognised IANA timezone.");
            }
            user.setTimezone(request.timezone());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl());
        }

        user.setUpdatedAt(Instant.now());
        return UserResponse.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID userId) {
        try {
            return ZoneId.of(activeOrThrow(userId).getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private User activeOrThrow(UUID userId) {
        return users.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("User"));
    }
}
