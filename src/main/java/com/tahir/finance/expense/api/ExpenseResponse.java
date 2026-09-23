package com.tahir.finance.expense.api;

import com.tahir.finance.expense.domain.Expense;
import com.tahir.finance.expense.domain.ExpenseOrigin;
import com.tahir.finance.expense.domain.PaymentMethod;
import com.tahir.finance.expense.domain.ReimbursementState;

import java.time.Instant;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        long amountMinor,
        String currencyCode,
        Instant spentAt,
        UUID categoryId,
        UUID tripId,
        String merchant,
        String note,
        PaymentMethod paymentMethod,
        ExpenseOrigin origin,
        ReimbursementState reimbursement,
        boolean editable,
        Instant createdAt,
        Instant updatedAt) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmountMinor(),
                expense.getCurrencyCode(),
                expense.getSpentAt(),
                expense.getCategoryId(),
                expense.getTripId(),
                expense.getMerchant(),
                expense.getNote(),
                expense.getPaymentMethod(),
                expense.getOrigin(),
                expense.getReimbursement(),
                !expense.isDerived(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
