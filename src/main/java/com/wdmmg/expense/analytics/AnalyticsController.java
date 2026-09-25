package com.wdmmg.expense.analytics;

import com.wdmmg.expense.analytics.AnalyticsDtos.GroupShareItem;
import com.wdmmg.expense.analytics.AnalyticsDtos.PersonalAnalytics;
import com.wdmmg.expense.security.AuthUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    private final InsightsService insights;

    public AnalyticsController(AnalyticsService service, InsightsService insights) {
        this.service = service;
        this.insights = insights;
    }

    /**
     * Personal spend analytics. With includeGroups=true (default) your share of every group expense is
     * counted alongside your personal expenses, so the numbers reflect what you actually spent.
     */
    @GetMapping("/summary")
    public PersonalAnalytics summary(@AuthenticationPrincipal AuthUser me,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "true") boolean includeGroups) {
        return service.personal(me.id(), from, to, includeGroups);
    }

    /** My shares of group expenses in a range, for showing next to personal expenses. */
    @GetMapping("/group-shares")
    public List<GroupShareItem> groupShares(@AuthenticationPrincipal AuthUser me,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.groupShares(me.id(), from, to);
    }

    /**
     * AI-written reading of the same figures as /summary. Cached while the numbers are unchanged;
     * refresh=true asks for a new take (rate-limited).
     */
    @GetMapping("/insights")
    public InsightsDtos.InsightsResponse insights(@AuthenticationPrincipal AuthUser me,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "true") boolean includeGroups,
                                                  @RequestParam(defaultValue = "false") boolean refresh) {
        return insights.insights(me.id(), from, to, includeGroups, refresh);
    }
}
