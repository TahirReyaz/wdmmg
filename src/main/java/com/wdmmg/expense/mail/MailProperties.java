package com.wdmmg.expense.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.mail.* – what the app sends as, and the mailbox it reads from.
 * SMTP connection details themselves live under spring.mail.*.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String from, String fromName, Inbound inbound) {

    public record Inbound(boolean enabled, String host, int port, String username, String password, String folder,
                          int pollSeconds, int maxPerPoll, boolean requireSenderAuth) {}
}
