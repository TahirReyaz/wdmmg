package com.tahir.finance.expense.api;

import com.tahir.finance.expense.domain.PaymentMethod;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Every field is optional; null means "leave as is". */
public record UpdateExpenseRequest(
        @Positive(message = "must be greater than zero") Long amountMinor,
        Instant spentAt,
        UUID categoryId,
        UUID tripId,
        @Size(max = 120) String merchant,
        @Size(max = 2000) String note,
        PaymentMethod paymentMethod) {
}
