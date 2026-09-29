package com.wdmmg.expense.mail.inbound;

import com.wdmmg.expense.mail.EmailSender;
import com.wdmmg.expense.mail.MailDeliveryException;
import com.wdmmg.expense.mail.MailProperties;
import com.wdmmg.expense.user.User;
import com.wdmmg.expense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Entry point for every inbound message, whatever the transport.
 * Stores it (idempotent by Message-ID), applies sender checks, routes it to the first
 * matching {@link InboundEmailHandler}, records the outcome and sends any reply.
 */
@Service
public class InboundEmailService {
    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);
    private static final int MAX_BODY = 20_000;

    private final InboundEmailRepository repo;
    private final UserRepository users;
    private final List<InboundEmailHandler> handlers;
    private final EmailSender sender;
    private final MailProperties props;

    public InboundEmailService(InboundEmailRepository repo, UserRepository users, List<InboundEmailHandler> handlers,
                               EmailSender sender, MailProperties props) {
        this.repo = repo;
        this.users = users;
        this.handlers = handlers;
        this.sender = sender;
        this.props = props;
    }

    /**
     * Deliberately not one big transaction: handlers may call an AI model for several seconds
     * and create records through their own transactional services.
     * A duplicate delivered concurrently is stopped by the unique message_id.
     *
     * @return the stored record, or null if this Message-ID was already handled
     */
    public InboundEmail receive(ReceivedEmail email) {
        if (repo.existsByMessageId(email.messageId())) {
            log.debug("Skipping already-processed message {}", email.messageId());
            return null;
        }

        InboundEmail rec = new InboundEmail();
        rec.setMessageId(email.messageId());
        rec.setFromAddress(truncate(email.fromAddress(), 320));
        rec.setFromName(truncate(email.fromName(), 200));
        rec.setSubject(truncate(email.subject(), 998));
        rec.setBodyText(truncate(email.text(), MAX_BODY));
        rec.setReceivedAt(email.receivedAt());
        rec.setStatus(InboundStatus.PROCESSING);
        // Claim the Message-ID before doing any work. If a retry of the same webhook (or a
        // second poll) races us, the unique constraint lets exactly one of them through.
        try {
            rec = repo.saveAndFlush(rec);
        } catch (DataIntegrityViolationException dup) {
            log.debug("Message {} is already being handled", email.messageId());
            return null;
        }
        log.info("Inbound email {} from {} – subject: '{}'", email.messageId(), email.fromAddress(), email.subject());

        HandlerResult result;
        if (SenderChecks.looksAutomated(email)) {
            result = HandlerResult.ignored("Automated message (bounce, auto-reply or list mail)");
        } else if (props.inbound() != null && props.inbound().requireSenderAuth()
                && !SenderChecks.senderAuthenticated(email.authenticationResults())) {
            result = HandlerResult.ignored("Sender failed SPF/DKIM/DMARC checks");
        } else {
            User user = users.findByEmailIgnoreCase(email.fromAddress()).filter(User::isEmailVerified).orElse(null);
            if (user != null) rec.setUserId(user.getId());
            result = dispatch(new InboundContext(email, user), rec);
        }

        rec.setStatus(result.status());
        rec.setResult(truncate(result.summary(), 4000));
        repo.save(rec);
        log.info("Inbound email {} → {} ({})", email.messageId(), result.status(), result.summary());

        if (result.reply() != null) {
            try {
                sender.send(result.reply());
            } catch (MailDeliveryException e) {
                log.warn("Reply to {} failed: {}", email.fromAddress(), e.getMessage());
            }
        }
        return rec;
    }

    private HandlerResult dispatch(InboundContext ctx, InboundEmail rec) {
        for (InboundEmailHandler h : handlers) {
            if (!h.supports(ctx)) continue;
            rec.setHandler(h.name());
            try {
                return h.handle(ctx);
            } catch (RuntimeException e) {
                log.error("Inbound handler {} failed on {}", h.name(), ctx.email().messageId(), e);
                return new HandlerResult(InboundStatus.FAILED, h.name() + " failed: " + e.getMessage(), null);
            }
        }
        return HandlerResult.ignored(ctx.fromKnownUser() ? "No handler for this message" : "Sender isn't a verified user");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
