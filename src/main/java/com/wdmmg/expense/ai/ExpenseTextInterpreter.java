package com.wdmmg.expense.ai;

/**
 * Turns natural language ("I had an ice cream for 30rs") into expense drafts.
 * Exactly one implementation is active, selected by app.ai.provider.
 */
public interface ExpenseTextInterpreter {
    /** Short id stored with processed emails, e.g. "none", "gemini". */
    String provider();

    /** @throws AiException if the model can't be reached or answers nonsense */
    Interpretation interpret(InterpretationRequest request);
}
