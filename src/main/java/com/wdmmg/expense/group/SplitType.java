package com.wdmmg.expense.group;

public enum SplitType {
    /** Split equally among the selected participants. */
    EQUAL,
    /** Each participant's exact amount is given; must add up to the total. */
    EXACT,
    /** Each participant's percentage is given; must add up to 100. */
    PERCENT,
    /** Split by weight units (e.g. 2 shares vs 1 share). */
    SHARES
}
