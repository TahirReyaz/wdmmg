package com.wdmmg.expense.user;

import com.wdmmg.expense.user.UserDtos.AuthResponse;
import com.wdmmg.expense.user.UserDtos.LoginRequest;
import com.wdmmg.expense.user.UserDtos.RegisterRequest;
import com.wdmmg.expense.user.UserDtos.RegisterResponse;
import com.wdmmg.expense.user.UserDtos.ResendCodeRequest;
import com.wdmmg.expense.user.UserDtos.ResendCodeResponse;
import com.wdmmg.expense.user.UserDtos.VerifyEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return auth.verifyEmail(req);
    }

    @PostMapping("/resend-code")
    public ResendCodeResponse resend(@Valid @RequestBody ResendCodeRequest req) {
        return auth.resend(req.email());
    }
}
