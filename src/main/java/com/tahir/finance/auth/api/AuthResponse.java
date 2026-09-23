package com.tahir.finance.auth.api;

import com.tahir.finance.user.api.UserResponse;

public record AuthResponse(UserResponse user, String accessToken, long expiresInSeconds) {
}
