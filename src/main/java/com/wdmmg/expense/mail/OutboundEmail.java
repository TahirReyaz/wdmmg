package com.wdmmg.expense.mail;

/**
 * A message to send. Always has a plain-text body; html is optional.
 * replyTo lets a reply thread back into the inbound mailbox when that matters.
 */
public record OutboundEmail(String to, String subject, String text, String html, String replyTo, String inReplyTo) {

    public static OutboundEmail of(String to, String subject, String text, String html) {
        return new OutboundEmail(to, subject, text, html, null, null);
    }
}
