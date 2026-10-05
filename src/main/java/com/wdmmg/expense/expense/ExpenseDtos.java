package com.wdmmg.expense.expense;

import com.wdmmg.expense.tag.TagDtos.TagRef;
import com.wdmmg.expense.category.CategoryDtos.CategoryResponse;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ExpenseDtos {
    private ExpenseDtos() {}

    public record ExpenseRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
            @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @NotNull Long categoryId,
            PaymentMethod paymentMethod,
            @Size(max = 1000) String notes,
            /** Optional tag; null = untagged. */
            Long tagId) {}

    public record ExpenseResponse(Long id, String name, BigDecimal amount, LocalDate date,
                                  CategoryResponse category, PaymentMethod paymentMethod, String notes,
                                  TagRef tag, Instant createdAt, Instant updatedAt) {
        public static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getName(), e.getAmount(), e.getDate(),
                    CategoryResponse.from(e.getCategory()), e.getPaymentMethod(), e.getNotes(),
                    TagRef.from(e.getTag()), e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    /** Count and sum of every expense matching a filter (not just one page). */
    public record ExpenseTotal(long count, BigDecimal total) {}

    public record ExpenseFilter(LocalDate from, LocalDate to, Long categoryId, PaymentMethod paymentMethod,
                                String q, BigDecimal minAmount, BigDecimal maxAmount, Long tagId) {}
}
