package com.wdmmg.expense.mail.inbound;

import com.wdmmg.expense.mail.OutboundEmail;

/**
 * Outcome of handling one message.
 *
 * @param summary short human-readable note stored with the message
 * @param reply   optional email to send back to the sender (e.g. "Added ₹30 – Ice cream")
 */
public record HandlerResult(InboundStatus status, String summary, OutboundEmail reply) {
    public static HandlerResult processed(String summary) {
        return new HandlerResult(InboundStatus.PROCESSED, summary, null);
    }

    public static HandlerResult ignored(String summary) {
        return new HandlerResult(InboundStatus.IGNORED, summary, null);
    }
}
