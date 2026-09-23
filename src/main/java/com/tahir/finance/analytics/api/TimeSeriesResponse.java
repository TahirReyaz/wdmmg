package com.tahir.finance.analytics.api;

import java.time.LocalDate;
import java.util.List;

public record TimeSeriesResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        String bucket,
        List<Point> series) {

    public record Point(LocalDate bucket, long totalMinor, long txnCount) {
    }
}
