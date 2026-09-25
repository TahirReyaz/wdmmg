package com.wdmmg.expense.mail;

/** Transport for outgoing mail. One implementation is active, chosen by app.mail.enabled. */
public interface EmailSender {
    /** @throws MailDeliveryException if the message could not be handed to the mail server */
    void send(OutboundEmail email);
}
