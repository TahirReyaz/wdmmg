package com.tahir.finance.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 200, message = "must be at least 10 characters") String password,
        @NotBlank @Size(min = 1, max = 80) String displayName,
        String baseCurrency,
        String timezone) {
}
