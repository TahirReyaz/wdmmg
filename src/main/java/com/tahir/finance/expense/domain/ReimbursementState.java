package com.tahir.finance.expense.domain;

public enum ReimbursementState {
    /** A plain personal expense - nobody owes anybody for it. */
    NOT_APPLICABLE,
    /** A group share that has not been settled yet. */
    PENDING,
    /** A group share covered by a settlement. */
    SETTLED
}
