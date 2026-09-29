package com.wdmmg.expense.mail.inbound.resend;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies webhook signatures in the Svix format Resend uses:
 * HMAC-SHA256 over "{svix-id}.{svix-timestamp}.{raw body}", keyed with the base64 part of the
 * "whsec_…" secret, sent as one or more space-separated "v1,{base64}" values in svix-signature.
 */
final class SvixSignature {
    /** Reject replays of old deliveries. */
    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final byte[] key;

    SvixSignature(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("empty webhook secret");
        String s = secret.trim();
        if (s.startsWith("whsec_")) s = s.substring("whsec_".length());
        this.key = Base64.getDecoder().decode(s);
    }

    enum Result { VALID, MISSING_HEADERS, STALE, BAD_SIGNATURE }

    Result verify(String id, String timestamp, String signatureHeader, byte[] body, Instant now) {
        if (id == null || timestamp == null || signatureHeader == null) return Result.MISSING_HEADERS;
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return Result.MISSING_HEADERS;
        }
        if (Math.abs(now.getEpochSecond() - ts) > TOLERANCE.toSeconds()) return Result.STALE;

        byte[] expected = Base64.getEncoder().encode(sign(id.trim(), ts, body));
        for (String part : signatureHeader.trim().split(" ")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !part.substring(0, comma).equals("v1")) continue;
            byte[] given = part.substring(comma + 1).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, given)) return Result.VALID;
        }
        return Result.BAD_SIGNATURE;
    }

    /** Also used by tests to produce valid signatures. */
    byte[] sign(String id, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
