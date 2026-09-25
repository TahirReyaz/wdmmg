package com.wdmmg.expense.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InsightsDtos {
    private InsightsDtos() {}

    public enum Status {
        /** Insights generated (or served from cache). */
        READY,
        /** Nothing spent in the period – nothing to analyse. */
        EMPTY,
        /** AI isn't configured on the server. */
        DISABLED
    }

    public enum Kind { TREND, CATEGORY, HABIT, ANOMALY, GROUP }

    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    public record Insight(String title, String detail, Kind kind, Sentiment sentiment) {}

    public record InsightsResponse(Status status, LocalDate from, LocalDate to, String headline, String summary,
                                   List<Insight> insights, List<String> suggestions, Instant generatedAt, String model,
                                   boolean cached) {
        static InsightsResponse of(Status status, LocalDate from, LocalDate to) {
            return new InsightsResponse(status, from, to, null, null, List.of(), List.of(), null, null, false);
        }

        InsightsResponse fromCache() {
            return new InsightsResponse(status, from, to, headline, summary, insights, suggestions, generatedAt, model, true);
        }
    }
}
