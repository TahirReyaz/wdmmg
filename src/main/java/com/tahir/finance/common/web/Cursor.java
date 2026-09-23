package com.tahir.finance.common.web;

import com.tahir.finance.common.error.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor: the sort key of the last row returned, base64url
 * encoded so clients treat it as a token rather than as coordinates they can
 * do arithmetic on.
 */
public record Cursor(Instant spentAt, UUID id) {

    public static String encode(Instant spentAt, UUID id) {
        String raw = spentAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.indexOf(':');
            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("BAD_CURSOR", "The pagination cursor is not valid.");
        }
    }
}
