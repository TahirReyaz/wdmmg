package com.tahir.finance.expense.api;

import com.tahir.finance.expense.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateExpenseRequest(
        @NotNull @Positive(message = "must be greater than zero") Long amountMinor,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three letter ISO-4217 code") String currencyCode,
        @NotNull Instant spentAt,
        UUID categoryId,
        UUID tripId,
        @Size(max = 120) String merchant,
        @Size(max = 2000) String note,
        PaymentMethod paymentMethod) {
}
