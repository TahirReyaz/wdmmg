package com.wdmmg.expense.goal;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class GoalDtos {
    private GoalDtos() {}

    public record GoalRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2) BigDecimal targetAmount,
            LocalDate targetDate) {}

    public enum Direction { ADD, WITHDRAW }

    public record FundsRequest(
            @NotNull Direction direction,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Size(max = 255) String note) {}

    /**
     * monthlyNeeded: what to set aside each month from now to hit the target on time
     * (null without a future target date or once reached).
     */
    public record GoalResponse(Long id, String name, BigDecimal targetAmount, LocalDate targetDate, BigDecimal savedAmount,
                               BigDecimal remaining, double progressPct, boolean reached, BigDecimal monthlyNeeded,
                               Instant createdAt) {
        static GoalResponse from(Goal g, LocalDate today) {
            BigDecimal remaining = g.getTargetAmount().subtract(g.getSavedAmount()).max(BigDecimal.ZERO);
            boolean reached = remaining.signum() == 0;
            double pct = Math.min(100.0, g.getSavedAmount().multiply(BigDecimal.valueOf(100))
                    .divide(g.getTargetAmount(), 1, RoundingMode.HALF_UP).doubleValue());
            BigDecimal monthly = null;
            if (!reached && g.getTargetDate() != null && g.getTargetDate().isAfter(today)) {
                long months = Math.max(1, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), g.getTargetDate().withDayOfMonth(1)));
                monthly = remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
            }
            return new GoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getTargetDate(), g.getSavedAmount(),
                    remaining, pct, reached, monthly, g.getCreatedAt());
        }
    }

    public record ContributionResponse(Long id, BigDecimal amount, String note, Instant createdAt) {
        static ContributionResponse from(GoalContribution c) {
            return new ContributionResponse(c.getId(), c.getAmount(), c.getNote(), c.getCreatedAt());
        }
    }
}
