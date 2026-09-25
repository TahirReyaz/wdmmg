package com.wdmmg.expense.verification;

import com.wdmmg.expense.mail.EmailSender;
import com.wdmmg.expense.mail.EmailTemplates;
import com.wdmmg.expense.mail.MailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the code once the database work is committed, off the request thread,
 * so a slow or failing mail server never blocks or rolls back sign-up.
 * The user can always ask for a new code from the verification screen.
 */
@Component
public class VerificationMailListener {
    private static final Logger log = LoggerFactory.getLogger(VerificationMailListener.class);

    private final EmailSender sender;
    private final EmailTemplates templates;

    public VerificationMailListener(EmailSender sender, EmailTemplates templates) {
        this.sender = sender;
        this.templates = templates;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIssued(VerificationCodeIssued event) {
        try {
            sender.send(templates.verificationCode(event.email(), event.name(), event.code(), event.ttlMinutes()));
        } catch (MailDeliveryException e) {
            log.error("Verification email to {} failed: {}", event.email(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }
}
