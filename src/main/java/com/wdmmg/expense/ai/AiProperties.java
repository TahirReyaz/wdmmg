package com.wdmmg.expense.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.ai.* – which model (if any) turns free text into expenses. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String currency, Gemini gemini, Insights insights) {
    public record Gemini(String apiKey, String model, String baseUrl) {}

    public record Insights(int cacheHours, int maxPerUserPerHour) {}
}
