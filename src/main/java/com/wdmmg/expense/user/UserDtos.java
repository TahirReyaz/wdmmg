package com.wdmmg.expense.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100, message = "must be at least 8 characters") String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    /** Sign-up doesn't sign you in: the address must be confirmed with the emailed code first. */
    public record RegisterResponse(String email, boolean verificationRequired, int codeExpiresInSeconds, long resendAfterSeconds) {}

    public record VerifyEmailRequest(@NotBlank @Email String email, @NotBlank @Size(max = 12) String code) {}

    public record ResendCodeRequest(@NotBlank @Email String email) {}

    public record ResendCodeResponse(int codeExpiresInSeconds, long resendAfterSeconds) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100, message = "must be at least 8 characters") String newPassword) {}

    public record UserResponse(Long id, String name, String email, Role role, String avatarUrl, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), avatarPath(u), u.getCreatedAt());
        }
    }

    /** Minimal public view of a user (e.g. group members). */
    public record UserSummary(Long id, String name, String email, String avatarUrl) {
        public static UserSummary from(User u) {
            return new UserSummary(u.getId(), u.getName(), u.getEmail(), avatarPath(u));
        }
    }

    /** Relative URL of the user's picture (the frontend prefixes the API origin), or null. */
    public static String avatarPath(User u) {
        return u.getAvatarKey() == null ? null : "/api/avatars/" + u.getAvatarKey();
    }

    public record AuthResponse(String token, UserResponse user) {}
}
