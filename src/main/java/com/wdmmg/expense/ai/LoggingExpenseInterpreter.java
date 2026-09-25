package com.wdmmg.expense.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Used when AI is off: records what would have been sent to the model and proposes nothing. */
@Component
@ConditionalOnExpression("!'${app.ai.provider:none}'.equalsIgnoreCase('gemini')")
public class LoggingExpenseInterpreter implements ExpenseTextInterpreter {
    private static final Logger log = LoggerFactory.getLogger(LoggingExpenseInterpreter.class);

    @Override
    public String provider() {
        return "none";
    }

    @Override
    public Interpretation interpret(InterpretationRequest r) {
        log.info("""
                
                ---- EXPENSE EMAIL (AI_PROVIDER=none – not parsed) ----
                User:    {}
                Subject: {}
                Today:   {} ({})
                
                {}
                -------------------------------------------------------""", r.userId(), r.subject(), r.today(), r.currency(), r.text());
        return Interpretation.none("AI parsing is off (AI_PROVIDER=none)");
    }
}
