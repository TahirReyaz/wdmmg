package com.tahir.finance.user.api;

import com.tahir.finance.auth.security.CurrentUser;
import com.tahir.finance.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public UserResponse me() {
        return users.profile(CurrentUser.id());
    }

    @PatchMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return users.updateProfile(CurrentUser.id(), request);
    }
}
