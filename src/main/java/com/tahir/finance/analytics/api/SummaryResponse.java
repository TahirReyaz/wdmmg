package com.tahir.finance.analytics.api;

import java.time.LocalDate;
import java.util.UUID;

public record SummaryResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        long totalMinor,
        long txnCount,
        long avgPerDayMinor,
        long largestMinor,
        TopCategory topCategory,
        Long previousTotalMinor,
        Double deltaPct) {

    public record TopCategory(UUID categoryId, String name, String colorHex, long totalMinor) {
    }
}
