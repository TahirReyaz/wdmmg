package com.tahir.finance.expense.service;

import com.tahir.finance.expense.domain.ExpenseOrigin;

import java.time.Instant;
import java.util.UUID;

/** Every filter the expense list supports. All fields are optional. */
public record ExpenseQuery(
        Instant from,
        Instant to,
        UUID categoryId,
        UUID tripId,
        ExpenseOrigin origin,
        Long minAmount,
        Long maxAmount,
        String search,
        Integer limit,
        String cursor) {
}
