package com.wdmmg.expense.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used while SMTP isn't configured (app.mail.enabled=false): writes the message to the log
 * so flows like email verification still work locally – read the code from the console.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(OutboundEmail email) {
        log.info("""
                
                ---- EMAIL (not sent: MAIL_ENABLED=false) ----
                To:      {}
                Subject: {}
                
                {}
                ----------------------------------------------""", email.to(), email.subject(), email.text());
    }
}
