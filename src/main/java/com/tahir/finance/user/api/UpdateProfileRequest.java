package com.tahir.finance.user.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 80) String displayName,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three letter ISO-4217 code") String baseCurrency,
        String timezone,
        String avatarUrl) {
}
