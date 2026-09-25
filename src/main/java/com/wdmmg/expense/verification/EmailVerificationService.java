package com.wdmmg.expense.verification;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.security.AppProperties;
import com.wdmmg.expense.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Issues and checks 6-digit email verification codes.
 * Limits: one code per cooldown window, N codes per hour, M wrong guesses per code.
 */
@Service
public class EmailVerificationService {
    public static final String INVALID_CODE = "INVALID_CODE";
    public static final String CODE_EXPIRED = "CODE_EXPIRED";
    public static final String TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS";
    public static final String RESEND_TOO_SOON = "RESEND_TOO_SOON";
    public static final String TOO_MANY_CODES = "TOO_MANY_CODES";

    private final EmailVerificationCodeRepository codes;
    private final VerificationProperties props;
    private final ApplicationEventPublisher events;
    private final byte[] hmacKey;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationCodeRepository codes, VerificationProperties props,
                                    ApplicationEventPublisher events, AppProperties app) {
        this.codes = codes;
        this.props = props;
        this.events = events;
        this.hmacKey = ("email-verification:" + app.jwt().secret()).getBytes(StandardCharsets.UTF_8);
    }

    public int ttlSeconds() {
        return props.codeTtlMinutes() * 60;
    }

    public int cooldownSeconds() {
        return props.resendCooldownSeconds();
    }

    /** Seconds until another code may be sent (0 = now). */
    @Transactional(readOnly = true)
    public long secondsUntilResend(Long userId) {
        return codes.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(c -> Math.max(0, Duration.between(Instant.now(), c.getCreatedAt().plusSeconds(props.resendCooldownSeconds())).toSeconds()))
                .orElse(0L);
    }

    /** Sends a fresh code, or explains (429) why not yet. */
    @Transactional
    public void issue(User user) {
        Instant now = Instant.now();
        long wait = secondsUntilResend(user.getId());
        if (wait > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + wait + " seconds before requesting another code.", RESEND_TOO_SOON);
        }
        if (codes.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= props.maxCodesPerHour()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many codes requested. Try again in an hour.", TOO_MANY_CODES);
        }
        codes.consumeOutstanding(user.getId(), now);
        String code = "%06d".formatted(random.nextInt(1_000_000));
        EmailVerificationCode c = new EmailVerificationCode();
        c.setUserId(user.getId());
        c.setCodeHash(hash(user.getId(), code));
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(Duration.ofMinutes(props.codeTtlMinutes())));
        codes.save(c);
        events.publishEvent(new VerificationCodeIssued(user.getEmail(), user.getName(), code, props.codeTtlMinutes()));
    }

    /** Like {@link #issue} but quietly does nothing when a code was sent moments ago. */
    @Transactional
    public void issueIfAllowed(User user) {
        try {
            issue(user);
        } catch (ApiException e) {
            if (!RESEND_TOO_SOON.equals(e.getCode()) && !TOO_MANY_CODES.equals(e.getCode())) throw e;
        }
    }

    /**
     * Checks a code and marks the user verified. Wrong guesses are counted even though
     * an exception is thrown, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(User user, String rawCode) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        EmailVerificationCode c = codes.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDescIdDesc(user.getId())
                .orElseThrow(() -> expired());
        Instant now = Instant.now();
        if (c.getExpiresAt().isBefore(now)) throw expired();
        if (c.getAttempts() >= props.maxAttempts()) throw tooMany();

        c.setAttempts(c.getAttempts() + 1);
        boolean ok = code.matches("\\d{6}")
                && MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                                         hash(user.getId(), code).getBytes(StandardCharsets.US_ASCII));
        if (!ok) {
            int left = props.maxAttempts() - c.getAttempts();
            if (left <= 0) throw tooMany();
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That code isn't right. " + left + (left == 1 ? " attempt" : " attempts") + " left.", INVALID_CODE);
        }
        c.setConsumedAt(now);
        user.setEmailVerified(true);
    }

    /** Housekeeping: codes are useless after a day. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOld() {
        codes.deleteCreatedBefore(Instant.now().minus(Duration.ofDays(1)));
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.BAD_REQUEST, "This code has expired. Send yourself a new one.", CODE_EXPIRED);
    }

    private static ApiException tooMany() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Too many incorrect attempts. Send yourself a new code.", TOO_MANY_ATTEMPTS);
    }

    private String hash(Long userId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
