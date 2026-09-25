package com.wdmmg.expense.ai;

import java.util.List;

/**
 * What the model made of a message.
 *
 * @param isExpense false for questions, greetings, forwarded newsletters …
 * @param reason    one short sentence, shown to the user when nothing was added
 */
public record Interpretation(boolean isExpense, String reason, List<ExpenseDraft> expenses) {
    public static Interpretation none(String reason) {
        return new Interpretation(false, reason, List.of());
    }
}
