package com.wdmmg.expense.recurring;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FrequencyTest {
    @Test
    void monthlyDoesNotDriftFromMonthEnd() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertThat(Frequency.MONTHLY.occurrence(start, 1, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(Frequency.MONTHLY.occurrence(start, 1, 2)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void everyTwoWeeks() {
        assertThat(Frequency.WEEKLY.occurrence(LocalDate.of(2026, 9, 1), 2, 2)).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void fastForwardPicksFirstDateOnOrAfterToday() {
        RecurringExpense r = new RecurringExpense();
        r.setFrequency(Frequency.MONTHLY);
        r.setIntervalCount(1);
        r.setStartDate(LocalDate.of(2026, 1, 5));
        RecurringService.fastForward(r, LocalDate.of(2026, 9, 23));
        assertThat(r.getNextDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));

        RecurringService.fastForward(r, LocalDate.of(2026, 10, 5));
        assertThat(r.getNextDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void endDateStopsSchedule() {
        RecurringExpense r = new RecurringExpense();
        r.setFrequency(Frequency.YEARLY);
        r.setIntervalCount(1);
        r.setStartDate(LocalDate.of(2020, 3, 1));
        r.setEndDate(LocalDate.of(2025, 12, 31));
        RecurringService.fastForward(r, LocalDate.of(2026, 9, 23));
        assertThat(r.getNextDueDate()).isNull();
    }
}
