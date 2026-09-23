package com.tahir.finance.expense.service;

import com.tahir.finance.common.Uuid7;
import com.tahir.finance.expense.domain.Expense;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Keeps {@code expense_daily_rollup} in step with the ledger.
 * <p>
 * The upsert runs inside the same transaction as the expense write, so a chart
 * can never disagree with the expense list. A nightly rebuild job (added with
 * the scheduler) is the self-heal for anything that ever slips through.
 */
@Service
public class RollupService {

    private static final String UPSERT = """
            INSERT INTO expense_daily_rollup
                (user_id, day, category_id, group_id, origin, currency_code, total_minor, txn_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, day, category_id, group_id, origin, currency_code)
            DO UPDATE SET total_minor = expense_daily_rollup.total_minor + EXCLUDED.total_minor,
                          txn_count   = expense_daily_rollup.txn_count   + EXCLUDED.txn_count
            """;

    private static final String PRUNE_EMPTY = """
            DELETE FROM expense_daily_rollup
             WHERE user_id = ? AND day = ? AND category_id = ? AND group_id = ?
               AND origin = ? AND currency_code = ? AND txn_count <= 0
            """;

    private final JdbcTemplate jdbc;

    public RollupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void applyAdd(Expense expense, ZoneId zone) {
        apply(expense, zone, expense.getAmountMinor(), 1);
    }

    @Transactional
    public void applyRemove(Expense expense, ZoneId zone) {
        apply(expense, zone, -expense.getAmountMinor(), -1);
    }

    private void apply(Expense expense, ZoneId zone, long amountDelta, int countDelta) {
        LocalDate day = expense.getSpentAt().atZone(zone).toLocalDate();
        UUID categoryId = expense.getCategoryId() == null ? Uuid7.NIL : expense.getCategoryId();
        UUID groupId = Uuid7.NIL;   // personal ledger; group attribution lands with the group module
        String origin = expense.getOrigin().name();

        jdbc.update(UPSERT,
                expense.getUserId(), day, categoryId, groupId, origin,
                expense.getCurrencyCode(), amountDelta, countDelta);

        if (countDelta < 0) {
            jdbc.update(PRUNE_EMPTY,
                    expense.getUserId(), day, categoryId, groupId, origin, expense.getCurrencyCode());
        }
    }
}
