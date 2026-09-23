package com.wdmmg.expense.recurring;

import com.wdmmg.expense.category.CategoryDtos.CategoryResponse;
import com.wdmmg.expense.expense.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RecurringDtos {
    private RecurringDtos() {}

    public record RecurringRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
            @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull Long categoryId,
            PaymentMethod paymentMethod,
            @Size(max = 1000) String notes,
            @NotNull Frequency frequency,
            @Min(1) @Max(365) Integer intervalCount,
            @NotNull LocalDate startDate,
            LocalDate endDate) {}

    public record RecurringResponse(Long id, String name, BigDecimal amount, CategoryResponse category,
                                    PaymentMethod paymentMethod, String notes, Frequency frequency, int intervalCount,
                                    LocalDate startDate, LocalDate endDate, LocalDate nextDueDate, boolean active,
                                    long pendingCount) {
        static RecurringResponse from(RecurringExpense r, long pending) {
            return new RecurringResponse(r.getId(), r.getName(), r.getAmount(), CategoryResponse.from(r.getCategory()),
                    r.getPaymentMethod(), r.getNotes(), r.getFrequency(), r.getIntervalCount(), r.getStartDate(),
                    r.getEndDate(), r.getNextDueDate(), r.isActive(), pending);
        }
    }

    public record OccurrenceResponse(Long id, Long recurringId, String name, BigDecimal amount, LocalDate dueDate,
                                     CategoryResponse category, PaymentMethod paymentMethod, OccurrenceStatus status,
                                     Long expenseId) {
        static OccurrenceResponse from(RecurringOccurrence o) {
            RecurringExpense r = o.getRecurring();
            return new OccurrenceResponse(o.getId(), r.getId(), r.getName(), o.getAmount(), o.getDueDate(),
                    CategoryResponse.from(r.getCategory()), r.getPaymentMethod(), o.getStatus(), o.getExpenseId());
        }
    }

    /** Optional tweaks when confirming (the bill might differ this month). */
    public record ConfirmRequest(
            @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            LocalDate date) {}
}
