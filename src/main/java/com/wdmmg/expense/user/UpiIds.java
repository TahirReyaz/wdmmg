package com.wdmmg.expense.user;

import java.util.Locale;

/**
 * UPI IDs (VPAs) look like "handle@psp" – e.g. "tahir.reyaz@okhdfcbank", "9876543210@ybl".
 * They're case-insensitive, so they're stored trimmed and lower-cased.
 */
public final class UpiIds {
    /** Also used for request validation; the empty string means "no UPI ID". */
    public static final String PATTERN = "^$|^\\s*[A-Za-z0-9][A-Za-z0-9._-]{1,255}@[A-Za-z][A-Za-z0-9]{1,63}\\s*$";
    public static final String MESSAGE = "must look like name@bank, e.g. yourname@okhdfcbank";

    private UpiIds() {}

    /** Blank → null, otherwise trimmed and lower-cased. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
