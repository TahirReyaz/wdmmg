package com.wdmmg.expense.mail.inbound;

public enum InboundStatus {
    /** Claimed by one worker and being handled right now (guards against duplicate deliveries). */
    PROCESSING,
    /** A handler dealt with it. */
    PROCESSED,
    /** Deliberately not acted on (unknown sender, auto-reply, failed sender checks …). */
    IGNORED,
    /** A handler threw; see result. */
    FAILED
}
