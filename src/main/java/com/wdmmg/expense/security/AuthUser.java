package com.wdmmg.expense.security;

import com.wdmmg.expense.user.Role;

/** The authenticated principal available via @AuthenticationPrincipal. */
public record AuthUser(Long id, String email, String name, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
