package com.tahir.finance.analytics.service;

import com.tahir.finance.analytics.api.CategoryBreakdownResponse;
import com.tahir.finance.analytics.api.SummaryResponse;
import com.tahir.finance.analytics.api.TimeSeriesResponse;
import com.tahir.finance.common.error.ApiException;
import com.tahir.finance.user.domain.User;
import com.tahir.finance.user.domain.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every chart reads {@code expense_daily_rollup}, never the base table.
 * A year of one user's spending is a few hundred rollup rows, so these
 * aggregate in single-digit milliseconds on an index-only scan.
 */
@Service
public class AnalyticsService {

    private static final Set<String> BUCKETS = Set.of("day", "week", "month");
    private static final UUID NIL = new UUID(0L, 0L);
    private static final int MAX_RANGE_DAYS = 366 * 5;

    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public AnalyticsService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    // ---- summary ------------------------------------------------------------

    @Transactional(readOnly = true)
    public SummaryResponse summary(UUID userId, LocalDate from, LocalDate to, String currency) {
        validateRange(from, to);
        String code = currencyOrDefault(userId, currency);

        Totals current = totals(userId, from, to, code);

        long days = Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        Totals previous = totals(userId, previousFrom, previousTo, code);

        Double deltaPct = previous.total() == 0
                ? null
                : Math.round((current.total() - previous.total()) * 10_000.0 / previous.total()) / 100.0;

        SummaryResponse.TopCategory top = jdbc.query("""
                        SELECT r.category_id, c.name, c.color_hex, SUM(r.total_minor) AS total
                          FROM expense_daily_rollup r
                          LEFT JOIN expense_categories c ON c.id = r.category_id
                         WHERE r.user_id = ? AND r.day BETWEEN ? AND ? AND r.currency_code = ?
                         GROUP BY r.category_id, c.name, c.color_hex
                         ORDER BY total DESC
                         LIMIT 1
                        """,
                (ResultSetExtractor<SummaryResponse.TopCategory>) rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    UUID categoryId = (UUID) rs.getObject("category_id");
                    String name = rs.getString("name");
                    return new SummaryResponse.TopCategory(
                            NIL.equals(categoryId) ? null : categoryId,
                            name == null ? "Uncategorised" : name,
                            rs.getString("color_hex"),
                            rs.getLong("total"));
                },
                userId, from, to, code);

        return new SummaryResponse(
                from, to, code,
                current.total(),
                current.count(),
                current.total() / days,
                largestSingleExpense(userId, from, to, code),
                top,
                previous.total(),
                deltaPct);
    }

    // ---- breakdown by category ----------------------------------------------

    @Transactional(readOnly = true)
    public CategoryBreakdownResponse byCategory(UUID userId, LocalDate from, LocalDate to, String currency) {
        validateRange(from, to);
        String code = currencyOrDefault(userId, currency);

        List<Object[]> rows = jdbc.query("""
                        SELECT r.category_id,
                               COALESCE(c.name, 'Uncategorised') AS name,
                               c.color_hex,
                               SUM(r.total_minor) AS total,
                               SUM(r.txn_count)   AS txns
                          FROM expense_daily_rollup r
                          LEFT JOIN expense_categories c ON c.id = r.category_id
                         WHERE r.user_id = ? AND r.day BETWEEN ? AND ? AND r.currency_code = ?
                         GROUP BY r.category_id, c.name, c.color_hex
                         ORDER BY total DESC
                        """,
                (rs, rowNum) -> new Object[]{
                        rs.getObject("category_id"),
                        rs.getString("name"),
                        rs.getString("color_hex"),
                        rs.getLong("total"),
                        rs.getLong("txns")},
                userId, from, to, code);

        long total = rows.stream().mapToLong(row -> (Long) row[3]).sum();

        List<CategoryBreakdownResponse.Slice> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long amount = (Long) row[3];
            UUID categoryId = (UUID) row[0];
            series.add(new CategoryBreakdownResponse.Slice(
                    NIL.equals(categoryId) ? null : categoryId,
                    (String) row[1],
                    (String) row[2],
                    amount,
                    (Long) row[4],
                    total == 0 ? 0 : Math.round(amount * 10_000.0 / total) / 100.0));
        }

        return new CategoryBreakdownResponse(from, to, code, total, series);
    }

    // ---- time series --------------------------------------------------------

    @Transactional(readOnly = true)
    public TimeSeriesResponse timeSeries(UUID userId, LocalDate from, LocalDate to,
                                         String bucket, String currency, UUID categoryId) {
        validateRange(from, to);

        String unit = bucket == null ? "day" : bucket.toLowerCase();
        if (!BUCKETS.contains(unit)) {
            throw ApiException.badRequest("BAD_BUCKET", "bucket must be one of day, week or month.");
        }

        String code = currencyOrDefault(userId, currency);

        // date_trunc's unit is validated against a fixed allow-list above, so it
        // can be interpolated safely; every value is still a bound parameter.
        StringBuilder sql = new StringBuilder("""
                SELECT date_trunc('%s', r.day)::date AS bucket,
                       SUM(r.total_minor) AS total,
                       SUM(r.txn_count)   AS txns
                  FROM expense_daily_rollup r
                 WHERE r.user_id = ? AND r.day BETWEEN ? AND ? AND r.currency_code = ?
                """.formatted(unit));

        List<Object> args = new ArrayList<>(List.of(userId, from, to, code));
        if (categoryId != null) {
            sql.append("   AND r.category_id = ?\n");
            args.add(categoryId);
        }
        sql.append(" GROUP BY 1\n ORDER BY 1");

        List<TimeSeriesResponse.Point> series = jdbc.query(sql.toString(),
                (rs, rowNum) -> new TimeSeriesResponse.Point(
                        rs.getObject("bucket", LocalDate.class),
                        rs.getLong("total"),
                        rs.getLong("txns")),
                args.toArray());

        return new TimeSeriesResponse(from, to, code, unit, series);
    }

    // ---- helpers ------------------------------------------------------------

    private Totals totals(UUID userId, LocalDate from, LocalDate to, String currency) {
        return jdbc.query("""
                        SELECT COALESCE(SUM(total_minor), 0) AS total,
                               COALESCE(SUM(txn_count), 0)   AS txns
                          FROM expense_daily_rollup
                         WHERE user_id = ? AND day BETWEEN ? AND ? AND currency_code = ?
                        """,
                (ResultSetExtractor<Totals>) rs ->
                        rs.next() ? new Totals(rs.getLong("total"), rs.getLong("txns")) : new Totals(0, 0),
                userId, from, to, currency);
    }

    private long largestSingleExpense(UUID userId, LocalDate from, LocalDate to, String currency) {
        Long largest = jdbc.queryForObject("""
                        SELECT COALESCE(MAX(amount_minor), 0)
                          FROM expenses
                         WHERE user_id = ? AND deleted_at IS NULL AND currency_code = ?
                           AND spent_at >= ?::date AND spent_at < (?::date + INTERVAL '1 day')
                        """,
                Long.class, userId, currency, from.toString(), to.toString());
        return largest == null ? 0 : largest;
    }

    private String currencyOrDefault(UUID userId, String currency) {
        if (currency != null && currency.length() == 3) {
            return currency.toUpperCase();
        }
        return users.findById(userId).map(User::getBaseCurrency).orElse("INR");
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("MISSING_RANGE", "Both 'from' and 'to' are required.");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("BAD_RANGE", "'to' must not be earlier than 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_WIDE", "The range may not exceed five years.");
        }
    }

    private record Totals(long total, long count) {
    }
}
