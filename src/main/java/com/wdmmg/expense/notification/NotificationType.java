package com.wdmmg.expense.notification;

public enum NotificationType {
    /** Someone added you to a group. */
    GROUP_ADDED,
    /** A shared expense you're part of was added. */
    GROUP_EXPENSE,
    /** Someone recorded that they paid you back. */
    PAYMENT_RECEIVED,
    /** A recurring expense is due and needs confirming. */
    RECURRING_DUE,
    /** Monthly nudge to record salary. */
    SALARY_DUE
}
