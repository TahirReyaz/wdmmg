package com.tahir.finance.analytics.api;

import com.tahir.finance.analytics.service.AnalyticsService;
import com.tahir.finance.auth.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {

        return analytics.summary(CurrentUser.id(), from, to, currency);
    }

    @GetMapping("/by-category")
    public CategoryBreakdownResponse byCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String currency) {

        return analytics.byCategory(CurrentUser.id(), from, to, currency);
    }

    @GetMapping("/time-series")
    public TimeSeriesResponse timeSeries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String bucket,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) UUID categoryId) {

        return analytics.timeSeries(CurrentUser.id(), from, to, bucket, currency, categoryId);
    }
}
