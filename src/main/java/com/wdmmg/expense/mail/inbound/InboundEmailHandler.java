package com.wdmmg.expense.mail.inbound;

/**
 * Extension point for inbound mail. Handlers are tried in @Order; the first whose
 * {@link #supports} returns true handles the message. Add a new feature (receipts,
 * "summary please" commands, …) by adding a bean – nothing else changes.
 */
public interface InboundEmailHandler {
    /** Stored with each message this handler processed. */
    String name();

    boolean supports(InboundContext context);

    HandlerResult handle(InboundContext context);
}
