package com.wdmmg.expense.user;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.security.AuthUser;
import com.wdmmg.expense.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser me) {
        return UserResponse.from(load(me));
    }

    @PutMapping("/me")
    @Transactional
    public UserResponse update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest req) {
        User u = load(me);
        u.setName(req.name().trim());
        // Absent keeps the saved UPI ID (older clients only send name); "" removes it.
        if (req.upiId() != null) u.setUpiId(UpiIds.normalize(req.upiId()));
        return UserResponse.from(u);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest req) {
        User u = load(me);
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
    }

    private User load(AuthUser me) {
        return users.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
    }
}
