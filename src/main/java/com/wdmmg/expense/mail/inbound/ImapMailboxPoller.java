package com.wdmmg.expense.mail.inbound;

import com.wdmmg.expense.mail.MailProperties;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads new mail from an IMAP mailbox (Google Workspace: imap.gmail.com:993, IMAP enabled,
 * App Password) on a fixed delay. Each unseen message is handed to
 * {@link InboundEmailService} and then flagged \Seen so it isn't picked up again.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
public class ImapMailboxPoller {
    private static final Logger log = LoggerFactory.getLogger(ImapMailboxPoller.class);

    private final MailProperties.Inbound cfg;
    private final InboundEmailService service;
    private final AtomicBoolean running = new AtomicBoolean();

    public ImapMailboxPoller(MailProperties props, InboundEmailService service) {
        this.cfg = props.inbound();
        this.service = service;
    }

    @Scheduled(initialDelayString = "15", fixedDelayString = "${app.mail.inbound.poll-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void scheduledPoll() {
        poll();
    }

    /** @return number of messages handed to the service, or -1 if a poll was already running / not configured */
    public int poll() {
        if (isBlank(cfg.username()) || isBlank(cfg.password())) {
            log.warn("Inbound mail is enabled but MAIL_IMAP_USERNAME / MAIL_IMAP_PASSWORD aren't set – skipping poll");
            return -1;
        }
        if (!running.compareAndSet(false, true)) return -1;
        try {
            return fetch();
        } catch (MessagingException e) {
            log.error("Inbound mail poll failed ({}:{}): {}", cfg.host(), cfg.port(), e.getMessage());
            return -1;
        } finally {
            running.set(false);
        }
    }

    private int fetch() throws MessagingException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.host", cfg.host());
        p.put("mail.imaps.port", String.valueOf(cfg.port()));
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "15000");
        p.put("mail.imaps.timeout", "30000");
        Session session = Session.getInstance(p);

        Store store = session.getStore("imaps");
        store.connect(cfg.host(), cfg.port(), cfg.username(), cfg.password());
        try {
            Folder folder = store.getFolder(cfg.folder());
            folder.open(Folder.READ_WRITE);
            try {
                Message[] unseen = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                int n = Math.min(unseen.length, Math.max(1, cfg.maxPerPoll()));
                for (int i = 0; i < n; i++) {
                    Message message = unseen[i];
                    try {
                        service.receive(MimeMessageReader.read((MimeMessage) message));
                    } catch (Exception e) {
                        // Don't let one bad message block the queue; it's flagged seen below.
                        log.error("Could not process inbound message #{}: {}", message.getMessageNumber(), e.toString());
                    }
                    message.setFlag(Flags.Flag.SEEN, true);
                }
                if (n > 0) log.info("Inbound mail: processed {} of {} unseen message(s)", n, unseen.length);
                return n;
            } finally {
                folder.close(false);
            }
        } finally {
            store.close();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
