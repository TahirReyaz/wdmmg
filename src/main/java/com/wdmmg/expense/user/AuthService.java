package com.wdmmg.expense.user;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.security.JwtService;
import com.wdmmg.expense.user.UserDtos.AuthResponse;
import com.wdmmg.expense.user.UserDtos.LoginRequest;
import com.wdmmg.expense.user.UserDtos.RegisterRequest;
import com.wdmmg.expense.user.UserDtos.RegisterResponse;
import com.wdmmg.expense.user.UserDtos.ResendCodeResponse;
import com.wdmmg.expense.user.UserDtos.UserResponse;
import com.wdmmg.expense.user.UserDtos.VerifyEmailRequest;
import com.wdmmg.expense.verification.EmailVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EmailVerificationService verification;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, EmailVerificationService verification) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.verification = verification;
    }

    /**
     * Creates the account unverified and emails a code. Signing up again with an address
     * that was never verified replaces the pending details (typo'd name, forgotten password)
     * – harmless, because the account can't be used without access to the inbox.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u != null && u.isEmailVerified()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (u == null) {
            u = new User();
            u.setEmail(email);
            u.setRole(Role.USER);
        }
        u.setName(req.name().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        users.save(u);
        verification.issueIfAllowed(u);
        return new RegisterResponse(email, true, verification.ttlSeconds(), verification.secondsUntilResend(u.getId()));
    }

    /** Unverified accounts get a fresh code and a 403 the client uses to show the code screen. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!u.isEmailVerified()) {
            verification.issueIfAllowed(u);
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Confirm your email to sign in. We've sent a code to " + u.getEmail() + ".", EMAIL_NOT_VERIFIED);
        }
        return new AuthResponse(jwt.generate(u), UserResponse.from(u));
    }

    /** Correct code → verified and signed in. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verifyEmail(VerifyEmailRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That code isn't right.", EmailVerificationService.INVALID_CODE));
        if (u.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "This email is already confirmed. Sign in instead.", "ALREADY_VERIFIED");
        }
        verification.verify(u, req.code());
        return new AuthResponse(jwt.generate(u), UserResponse.from(u));
    }

    /**
     * Sends another code. Answers the same way for unknown or already-verified addresses
     * so the endpoint can't be used to discover accounts.
     */
    @Transactional
    public ResendCodeResponse resend(String rawEmail) {
        User u = users.findByEmailIgnoreCase(rawEmail.trim()).orElse(null);
        if (u != null && !u.isEmailVerified()) {
            verification.issue(u);
        }
        return new ResendCodeResponse(verification.ttlSeconds(), verification.cooldownSeconds());
    }
}
