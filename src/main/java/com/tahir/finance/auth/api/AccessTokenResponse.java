package com.tahir.finance.auth.api;

public record AccessTokenResponse(String accessToken, long expiresInSeconds) {
}
