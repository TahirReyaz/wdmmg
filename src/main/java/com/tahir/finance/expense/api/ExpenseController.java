package com.tahir.finance.expense.api;

import com.tahir.finance.auth.security.CurrentUser;
import com.tahir.finance.common.web.Page;
import com.tahir.finance.expense.domain.ExpenseOrigin;
import com.tahir.finance.expense.service.ExpenseQuery;
import com.tahir.finance.expense.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @GetMapping
    public Page<ExpenseResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tripId,
            @RequestParam(required = false) ExpenseOrigin origin,
            @RequestParam(required = false) Long minAmount,
            @RequestParam(required = false) Long maxAmount,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {

        return expenses.list(CurrentUser.id(), new ExpenseQuery(
                from, to, categoryId, tripId, origin, minAmount, maxAmount, search, limit, cursor));
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse get(@PathVariable UUID expenseId) {
        return expenses.get(CurrentUser.id(), expenseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody CreateExpenseRequest request,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return expenses.create(CurrentUser.id(), request, idempotencyKey);
    }

    @PatchMapping("/{expenseId}")
    public ExpenseResponse update(@PathVariable UUID expenseId,
                                  @Valid @RequestBody UpdateExpenseRequest request) {
        return expenses.update(CurrentUser.id(), expenseId, request);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID expenseId) {
        expenses.softDelete(CurrentUser.id(), expenseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{expenseId}/restore")
    public ExpenseResponse restore(@PathVariable UUID expenseId) {
        return expenses.restore(CurrentUser.id(), expenseId);
    }
}
