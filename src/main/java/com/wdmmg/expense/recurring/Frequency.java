package com.wdmmg.expense.recurring;

import java.time.LocalDate;

public enum Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY;

    /**
     * Date of the n-th occurrence (n = 0 is the start date). Always computed from the start date
     * rather than the previous occurrence, so e.g. "monthly from 31 Jan" gives 28/29 Feb then 31 Mar
     * instead of drifting to the 28th forever.
     */
    public LocalDate occurrence(LocalDate start, int every, int n) {
        long steps = (long) every * n;
        return switch (this) {
            case DAILY -> start.plusDays(steps);
            case WEEKLY -> start.plusWeeks(steps);
            case MONTHLY -> start.plusMonths(steps);
            case YEARLY -> start.plusYears(steps);
        };
    }
}
