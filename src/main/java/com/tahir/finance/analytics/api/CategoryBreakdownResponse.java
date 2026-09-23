package com.tahir.finance.analytics.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CategoryBreakdownResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        long total,
        List<Slice> series) {

    public record Slice(UUID categoryId, String name, String colorHex, long totalMinor, long txnCount, double pct) {
    }
}
