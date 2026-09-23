package com.tahir.finance.expense.service;

import com.tahir.finance.common.Uuid7;
import com.tahir.finance.common.error.ApiException;
import com.tahir.finance.common.web.Cursor;
import com.tahir.finance.common.web.Page;
import com.tahir.finance.expense.api.CreateExpenseRequest;
import com.tahir.finance.expense.api.ExpenseResponse;
import com.tahir.finance.expense.api.UpdateExpenseRequest;
import com.tahir.finance.expense.domain.Expense;
import com.tahir.finance.expense.domain.ExpenseOrigin;
import com.tahir.finance.expense.domain.ExpenseRepository;
import com.tahir.finance.expense.domain.ExpenseSpecifications;
import com.tahir.finance.user.domain.User;
import com.tahir.finance.user.domain.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExpenseService {

    private static final int MAX_PAGE = 200;

    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final RollupService rollups;

    public ExpenseService(ExpenseRepository expenses, UserRepository users, RollupService rollups) {
        this.expenses = expenses;
        this.users = users;
        this.rollups = rollups;
    }

    // ---- reads --------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> list(UUID userId, ExpenseQuery query) {
        int limit = Math.clamp(query.limit() == null ? 50 : query.limit(), 1, MAX_PAGE);
        Cursor cursor = Cursor.decode(query.cursor());

        var spec = ExpenseSpecifications.filter(
                userId,
                query.from(), query.to(),
                query.categoryId(), query.tripId(), query.origin(),
                query.minAmount(), query.maxAmount(), query.search(),
                cursor == null ? null : cursor.spentAt(),
                cursor == null ? null : cursor.id());

        Sort sort = Sort.by(Sort.Order.desc("spentAt"), Sort.Order.desc("id"));

        // limit + 1 tells us whether another page exists without a COUNT query.
        List<Expense> rows = expenses.findBy(spec, q -> q.sortBy(sort).limit(limit + 1).all());

        boolean hasMore = rows.size() > limit;
        List<Expense> pageRows = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore) {
            Expense last = pageRows.get(pageRows.size() - 1);
            nextCursor = Cursor.encode(last.getSpentAt(), last.getId());
        }

        return new Page<>(pageRows.stream().map(ExpenseResponse::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID userId, UUID expenseId) {
        return ExpenseResponse.from(activeOrThrow(userId, expenseId));
    }

    // ---- writes -------------------------------------------------------------

    @Transactional
    public ExpenseResponse create(UUID userId, CreateExpenseRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = expenses.findByIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return ExpenseResponse.from(existing.get());   // replay, not a second insert
            }
        }

        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));

        Expense expense = new Expense();
        expense.setId(Uuid7.generate());
        expense.setUserId(userId);
        expense.setAmountMinor(request.amountMinor());
        expense.setCurrencyCode(request.currencyCode() == null ? user.getBaseCurrency() : request.currencyCode());
        expense.setSpentAt(request.spentAt());
        expense.setCategoryId(request.categoryId());
        expense.setTripId(request.tripId());
        expense.setMerchant(trimToNull(request.merchant()));
        expense.setNote(trimToNull(request.note()));
        expense.setPaymentMethod(request.paymentMethod());
        expense.setOrigin(ExpenseOrigin.MANUAL);
        expense.setIdempotencyKey(trimToNull(idempotencyKey));

        Expense saved = expenses.save(expense);
        rollups.applyAdd(saved, zoneOf(user));

        return ExpenseResponse.from(saved);
    }

    @Transactional
    public ExpenseResponse update(UUID userId, UUID expenseId, UpdateExpenseRequest request) {
        Expense expense = activeOrThrow(userId, expenseId);
        refuseIfDerived(expense);

        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        ZoneId zone = zoneOf(user);

        // Back the old shape out of the rollup, then add the new one. Simpler
        // and safer than trying to compute a delta across changed dimensions.
        rollups.applyRemove(expense, zone);

        if (request.amountMinor() != null) {
            expense.setAmountMinor(request.amountMinor());
        }
        if (request.spentAt() != null) {
            expense.setSpentAt(request.spentAt());
        }
        if (request.categoryId() != null) {
            expense.setCategoryId(request.categoryId());
        }
        if (request.tripId() != null) {
            expense.setTripId(request.tripId());
        }
        if (request.merchant() != null) {
            expense.setMerchant(trimToNull(request.merchant()));
        }
        if (request.note() != null) {
            expense.setNote(trimToNull(request.note()));
        }
        if (request.paymentMethod() != null) {
            expense.setPaymentMethod(request.paymentMethod());
        }
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenses.save(expense);
        rollups.applyAdd(saved, zone);

        return ExpenseResponse.from(saved);
    }

    @Transactional
    public void softDelete(UUID userId, UUID expenseId) {
        Expense expense = activeOrThrow(userId, expenseId);
        refuseIfDerived(expense);

        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        rollups.applyRemove(expense, zoneOf(user));

        expense.setDeletedAt(Instant.now());
        expense.setIdempotencyKey(null);    // free the key so a retry can create a fresh row
        expenses.save(expense);
    }

    @Transactional
    public ExpenseResponse restore(UUID userId, UUID expenseId) {
        Expense expense = expenses.findOwned(expenseId, userId)
                .orElseThrow(() -> ApiException.notFound("Expense"));

        if (expense.getDeletedAt() == null) {
            throw ApiException.conflict("NOT_DELETED", "This expense is not deleted.");
        }

        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        expense.setDeletedAt(null);
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenses.save(expense);
        rollups.applyAdd(saved, zoneOf(user));

        return ExpenseResponse.from(saved);
    }

    // ---- helpers ------------------------------------------------------------

    private Expense activeOrThrow(UUID userId, UUID expenseId) {
        Expense expense = expenses.findOwned(expenseId, userId)
                .orElseThrow(() -> ApiException.notFound("Expense"));

        if (expense.getDeletedAt() != null) {
            throw ApiException.notFound("Expense");
        }
        return expense;
    }

    private void refuseIfDerived(Expense expense) {
        if (!expense.isDerived()) {
            return;
        }

        String source = expense.getOrigin() == ExpenseOrigin.GROUP
                ? "a group expense"
                : "a recurring payment";

        throw ApiException.forbidden(
                "DERIVED_EXPENSE",
                "This expense is generated from " + source + " and cannot be edited directly.",
                Map.of("origin", expense.getOrigin().name(),
                        "sourceId", expense.getOrigin() == ExpenseOrigin.GROUP
                                ? String.valueOf(expense.getSourceSplitId())
                                : String.valueOf(expense.getSourceOccurrenceId())));
    }

    private ZoneId zoneOf(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
