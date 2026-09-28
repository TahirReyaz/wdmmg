package com.wdmmg.expense.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * app.mail.* – what the app sends as, how it sends, and the mailbox it reads from.
 * SMTP connection details live under spring.mail.*; the Resend API key under app.mail.resend.
 *
 * @param provider "smtp" (default) or "resend" – used when enabled=true
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String provider, String from, String fromName, Resend resend, Inbound inbound) {

    public static final String SMTP = "smtp";
    public static final String RESEND = "resend";

    public MailProperties {
        provider = provider == null || provider.isBlank() ? SMTP : provider.trim().toLowerCase(Locale.ROOT);
        if (!provider.equals(SMTP) && !provider.equals(RESEND)) {
            throw new IllegalArgumentException("MAIL_PROVIDER must be 'smtp' or 'resend' (was '" + provider + "')");
        }
    }

    public record Resend(String apiKey, String baseUrl) {}

    public record Inbound(boolean enabled, String host, int port, String username, String password, String folder,
                          int pollSeconds, int maxPerPoll, boolean requireSenderAuth) {}
}
