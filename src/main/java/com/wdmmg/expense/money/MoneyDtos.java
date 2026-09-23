package com.wdmmg.expense.money;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class MoneyDtos {
    private MoneyDtos() {}

    public record IncomeRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            IncomeType type,
            @Size(max = 1000) String notes) {}

    public record IncomeResponse(Long id, String name, BigDecimal amount, LocalDate date, IncomeType type, String notes,
                                 Instant createdAt) {
        static IncomeResponse from(Income i) {
            return new IncomeResponse(i.getId(), i.getName(), i.getAmount(), i.getDate(), i.getType(), i.getNotes(), i.getCreatedAt());
        }
    }

    public record OpeningBalanceRequest(
            @NotNull @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date) {}

    public record SalarySettingsRequest(@NotNull Boolean reminder, @NotNull @Min(1) @Max(28) Integer day) {}

    /** Quick "my salary came in" entry from the monthly prompt. */
    public record SalaryRequest(
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            LocalDate date,
            @Size(max = 150) String name) {}

    /**
     * balance     = opening + money in − money out since the opening date
     * setAside    = total held in savings goals
     * available   = balance − setAside
     */
    public record MoneySummary(boolean configured, BigDecimal openingBalance, LocalDate openingDate,
                               BigDecimal balance, BigDecimal setAside, BigDecimal available,
                               BigDecimal monthIn, BigDecimal monthOut, Breakdown sinceOpening,
                               boolean salaryReminder, int salaryDay) {}

    public record Breakdown(BigDecimal income, BigDecimal expenses, BigDecimal groupBillsPaid,
                            BigDecimal paymentsSent, BigDecimal paymentsReceived) {}

    public enum ActivityKind { INCOME, EXPENSE, GROUP_BILL, PAYMENT_SENT, PAYMENT_RECEIVED }

    /** One movement in or out of the bank balance, with the balance right after it. */
    public record ActivityItem(String key, LocalDate date, ActivityKind kind, String title, String detail,
                               BigDecimal amount, BigDecimal balanceAfter, String link) {}

    public record SalaryPrompt(boolean due, String month, BigDecimal suggestedAmount, int salaryDay, boolean reminder) {}
}
