package com.wdmmg.expense.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wdmmg.expense.ai.AiException;
import com.wdmmg.expense.ai.AiProperties;
import com.wdmmg.expense.ai.GeminiClient;
import com.wdmmg.expense.analytics.AnalyticsDtos.CategoryStat;
import com.wdmmg.expense.analytics.AnalyticsDtos.DayPoint;
import com.wdmmg.expense.analytics.AnalyticsDtos.PersonalAnalytics;
import com.wdmmg.expense.analytics.InsightsDtos.Insight;
import com.wdmmg.expense.analytics.InsightsDtos.InsightsResponse;
import com.wdmmg.expense.analytics.InsightsDtos.Kind;
import com.wdmmg.expense.analytics.InsightsDtos.Sentiment;
import com.wdmmg.expense.analytics.InsightsDtos.Status;
import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plain-language read of the analytics page, written by Gemini.
 * The model only sees figures computed here (the same ones the charts show) and is told
 * not to invent any. Results are cached per user while the underlying numbers are unchanged,
 * and generation is rate-limited per user.
 */
@Service
public class InsightsService {
    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);
    private static final int MAX_CACHE = 500;

    private final AnalyticsService analytics;
    private final AppClock clock;
    private final GeminiClient gemini;
    private final AiProperties ai;
    private final ObjectMapper mapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();

    private record Cached(InsightsResponse response, Instant expires) {}

    /** Shape of Gemini's JSON reply. */
    public record Reply(String headline, String summary, List<ReplyInsight> insights, List<String> suggestions) {}

    public record ReplyInsight(String title, String detail, String kind, String sentiment) {}

    public InsightsService(AnalyticsService analytics, AppClock clock, GeminiClient gemini, AiProperties ai, ObjectMapper mapper) {
        this.analytics = analytics;
        this.clock = clock;
        this.gemini = gemini;
        this.ai = ai;
        this.mapper = mapper;
    }

    public InsightsResponse insights(Long userId, LocalDate from, LocalDate to, boolean includeGroups, boolean refresh) {
        PersonalAnalytics current = analytics.personal(userId, from, to, includeGroups);
        if (!gemini.isConfigured()) return InsightsResponse.of(Status.DISABLED, current.from(), current.to());
        if (current.transactionCount() == 0) return InsightsResponse.of(Status.EMPTY, current.from(), current.to());

        long days = ChronoUnit.DAYS.between(current.from(), current.to()) + 1;
        PersonalAnalytics previous = analytics.personal(userId, current.from().minusDays(days), current.from().minusDays(1), includeGroups);

        Map<String, Object> facts = facts(current, previous, days);
        String factsJson = toJson(facts);
        String key = userId + ":" + sha256(gemini.model() + "|" + factsJson);

        Cached hit = cache.get(key);
        if (!refresh && hit != null && hit.expires().isAfter(Instant.now())) return hit.response().fromCache();

        checkRate(userId);
        Reply reply;
        try {
            reply = gemini.generateJson(systemPrompt(currency()), "Spending data:\n" + factsJson, schema(), Reply.class);
        } catch (AiException e) {
            log.warn("Insights generation failed for user {}: {}", userId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't generate insights right now. Please try again shortly.", "AI_UNAVAILABLE");
        }

        InsightsResponse response = new InsightsResponse(Status.READY, current.from(), current.to(),
                clean(reply.headline(), 140), clean(reply.summary(), 800), insights(reply), suggestions(reply),
                Instant.now(), gemini.model(), false);
        store(key, response);
        return response;
    }

    // ------------------------------------------------------------------ facts sent to the model

    private Map<String, Object> facts(PersonalAnalytics c, PersonalAnalytics p, long days) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("currency", currency());
        LocalDate today = clock.today();
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("from", c.from().toString());
        period.put("to", c.to().toString());
        period.put("days", days);
        if (!c.to().isBefore(today) && !c.from().isAfter(today)) {
            // e.g. "this month" on the 24th: only part of the period has happened yet.
            period.put("inProgress", true);
            period.put("daysElapsed", ChronoUnit.DAYS.between(c.from(), today) + 1);
        }
        f.put("period", period);
        f.put("includesGroupShares", c.includeGroups());
        f.put("today", today.toString());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("spent", c.total());
        totals.put("personal", c.personalTotal());
        totals.put("groupShares", c.groupShareTotal());
        totals.put("transactions", c.transactionCount());
        totals.put("dailyAverage", c.dailyAverage());
        totals.put("previousPeriodSpent", c.previousTotal());
        totals.put("changeVsPreviousPct", c.changePct());
        f.put("totals", totals);

        Map<String, BigDecimal> prevByCat = new LinkedHashMap<>();
        for (CategoryStat s : p.byCategory()) prevByCat.put(s.name(), s.total());
        List<Map<String, Object>> cats = new ArrayList<>();
        for (CategoryStat s : c.byCategory().stream().limit(10).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name());
            m.put("spent", s.total());
            m.put("shareOfTotalPct", round1(s.pct()));
            m.put("count", s.count());
            BigDecimal before = prevByCat.getOrDefault(s.name(), BigDecimal.ZERO);
            m.put("previousPeriodSpent", before);
            m.put("changePct", pct(s.total(), before));
            cats.add(m);
        }
        f.put("categories", cats);
        // Categories that disappeared entirely are worth mentioning too.
        List<Map<String, Object>> dropped = new ArrayList<>();
        prevByCat.forEach((name, amount) -> {
            if (c.byCategory().stream().noneMatch(s -> s.name().equals(name)) && amount.signum() > 0) {
                dropped.add(Map.of("name", name, "previousPeriodSpent", amount));
            }
        });
        if (!dropped.isEmpty()) f.put("categoriesWithNoSpendThisPeriod", dropped);

        f.put("paymentMethods", c.byPaymentMethod().stream()
                .map(m -> Map.of("method", m.method(), "spent", m.total(), "count", m.count())).toList());
        f.put("sources", c.bySource().stream()
                .map(s -> Map.of("source", s.label(), "spent", s.total(), "count", s.count())).toList());
        f.put("largestExpenses", c.topExpenses().stream().limit(5)
                .map(t -> Map.of("name", t.name(), "amount", t.amount(), "date", t.date().toString(), "category", t.category(), "source", t.source()))
                .toList());

        // Days that haven't happened yet would read as "no spending".
        List<DayPoint> elapsed = c.daily().stream().filter(d -> !d.date().isAfter(today)).toList();
        if (!elapsed.isEmpty()) f.put("dailyPattern", dailyPattern(elapsed));
        f.put("last12Months", c.monthly().stream().map(m -> Map.of("month", m.month(), "spent", m.total())).toList());
        return f;
    }

    private Map<String, Object> dailyPattern(List<DayPoint> daily) {
        Map<DayOfWeek, BigDecimal> byDow = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> dowCount = new EnumMap<>(DayOfWeek.class);
        int zero = 0;
        DayPoint busiest = null;
        for (DayPoint d : daily) {
            DayOfWeek dow = d.date().getDayOfWeek();
            byDow.merge(dow, d.total(), BigDecimal::add);
            dowCount.merge(dow, 1, Integer::sum);
            if (d.total().signum() == 0) zero++;
            if (busiest == null || d.total().compareTo(busiest.total()) > 0) busiest = d;
        }
        Map<String, Object> avgByWeekday = new LinkedHashMap<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            if (!dowCount.containsKey(dow)) continue;
            avgByWeekday.put(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    byDow.get(dow).divide(BigDecimal.valueOf(dowCount.get(dow)), 2, RoundingMode.HALF_UP));
        }
        BigDecimal weekend = BigDecimal.ZERO, weekday = BigDecimal.ZERO;
        int weekendDays = 0, weekdayDays = 0;
        for (DayPoint d : daily) {
            boolean we = d.date().getDayOfWeek() == DayOfWeek.SATURDAY || d.date().getDayOfWeek() == DayOfWeek.SUNDAY;
            if (we) { weekend = weekend.add(d.total()); weekendDays++; } else { weekday = weekday.add(d.total()); weekdayDays++; }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("averageByWeekday", avgByWeekday);
        m.put("weekendDailyAverage", weekendDays == 0 ? null : weekend.divide(BigDecimal.valueOf(weekendDays), 2, RoundingMode.HALF_UP));
        m.put("weekdayDailyAverage", weekdayDays == 0 ? null : weekday.divide(BigDecimal.valueOf(weekdayDays), 2, RoundingMode.HALF_UP));
        m.put("daysWithNoSpending", zero);
        if (busiest != null) m.put("highestDay", Map.of("date", busiest.date().toString(), "spent", busiest.total()));
        return m;
    }

    // ------------------------------------------------------------------ prompt

    private static String systemPrompt(String currency) {
        return """
                You are a concise personal-finance analyst inside an expense tracker called "Where did my money go".
                You receive pre-computed spending figures (JSON) for one user and one period – the same numbers their charts show.
                Write a short, specific reading of them.

                Rules:
                - Use only figures present in the data, or simple arithmetic on them. Never invent amounts, merchants, reasons or dates.
                  If you infer a cause, say "likely" or "may".
                - Amounts are in %s. Write them with the currency symbol and thousands separators (for INR use Indian grouping, e.g. ₹1,23,456); no decimals unless under 100. Percentages as whole numbers.
                - Address the user as "you". Plain, calm English. No emojis, no markdown, no exclamation marks.
                - headline: the single most useful takeaway, at most 12 words.
                - summary: 2–3 sentences covering total spend, the change against the previous period, and what drove it.
                - insights: 3–5 distinct observations, most important first. Draw on: change vs previous period, which categories grew or shrank,
                  concentration in one category, weekday vs weekend habits, unusually large one-off expenses, payment methods, group shares.
                  Skip anything the data can't support. title: at most 6 words. detail: 1–2 sentences that cite the figures.
                  kind: TREND, CATEGORY, HABIT, ANOMALY or GROUP. sentiment: POSITIVE (spending down or healthy), NEGATIVE (rising or a concern), NEUTRAL.
                - suggestions: 1–3 practical next steps tied to specific figures in the data (e.g. a category to cap and by how much). No generic advice.
                - With fewer than 5 transactions, say the period has too little data for strong conclusions and keep insights to 1–2.
                - The period may be in progress (its end date can be today); don't treat a partial period as a full one without saying so.
                """.formatted(currency);
    }

    private static Map<String, Object> schema() {
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("type", "OBJECT");
        insight.put("properties", Map.of(
                "title", Map.of("type", "STRING"),
                "detail", Map.of("type", "STRING"),
                "kind", Map.of("type", "STRING", "enum", List.of("TREND", "CATEGORY", "HABIT", "ANOMALY", "GROUP")),
                "sentiment", Map.of("type", "STRING", "enum", List.of("POSITIVE", "NEUTRAL", "NEGATIVE"))));
        insight.put("required", List.of("title", "detail", "kind", "sentiment"));
        insight.put("propertyOrdering", List.of("title", "detail", "kind", "sentiment"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "OBJECT");
        root.put("properties", Map.of(
                "headline", Map.of("type", "STRING"),
                "summary", Map.of("type", "STRING"),
                "insights", Map.of("type", "ARRAY", "items", insight),
                "suggestions", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))));
        root.put("required", List.of("headline", "summary", "insights", "suggestions"));
        root.put("propertyOrdering", List.of("headline", "summary", "insights", "suggestions"));
        return root;
    }

    // ------------------------------------------------------------------ output hygiene

    private static List<Insight> insights(Reply r) {
        if (r.insights() == null) return List.of();
        return r.insights().stream().filter(Objects::nonNull)
                .filter(i -> i.title() != null && i.detail() != null && !i.detail().isBlank())
                .limit(5)
                .map(i -> new Insight(clean(i.title(), 80), clean(i.detail(), 400), parse(Kind.class, i.kind(), Kind.TREND),
                        parse(Sentiment.class, i.sentiment(), Sentiment.NEUTRAL)))
                .toList();
    }

    private static List<String> suggestions(Reply r) {
        if (r.suggestions() == null) return List.of();
        return r.suggestions().stream().filter(s -> s != null && !s.isBlank()).limit(3).map(s -> clean(s, 300)).toList();
    }

    /** Strips stray markdown the model may add despite instructions. */
    private static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.replace("**", "").replace("__", "").replaceAll("(?m)^\\s*[-*•]\\s+", "").strip();
        return t.length() <= max ? t : t.substring(0, max - 1).strip() + "…";
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ cache & limits

    private void checkRate(Long userId) {
        int max = ai.insights() == null || ai.insights().maxPerUserPerHour() <= 0 ? 20 : ai.insights().maxPerUserPerHour();
        Deque<Instant> q = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant cutoff = Instant.now().minus(Duration.ofHours(1));
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= max) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "You've refreshed insights a lot this hour. Try again later.", "AI_RATE_LIMITED");
            }
            q.addLast(Instant.now());
        }
    }

    private void store(String key, InsightsResponse r) {
        int hours = ai.insights() == null || ai.insights().cacheHours() <= 0 ? 6 : ai.insights().cacheHours();
        if (cache.size() >= MAX_CACHE) {
            Instant now = Instant.now();
            cache.entrySet().removeIf(e -> e.getValue().expires().isBefore(now));
            if (cache.size() >= MAX_CACHE) {
                cache.entrySet().stream().min(Comparator.comparing(e -> e.getValue().expires()))
                        .ifPresent(e -> cache.remove(e.getKey()));
            }
        }
        cache.put(key, new Cached(r, Instant.now().plus(Duration.ofHours(hours))));
    }

    private String currency() {
        return ai.currency() == null || ai.currency().isBlank() ? "INR" : ai.currency().trim().toUpperCase(Locale.ROOT);
    }

    private static Double pct(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) return null;
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 0, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
