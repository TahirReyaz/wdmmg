package com.wdmmg.expense.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/** Sends through the SMTP server configured under spring.mail.* (e.g. smtp.gmail.com). */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final MailProperties props;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void send(OutboundEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            boolean multipart = email.html() != null;
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            helper.setFrom(props.from(), props.fromName());
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            if (multipart) helper.setText(email.text(), email.html());
            else helper.setText(email.text(), false);
            if (email.replyTo() != null) helper.setReplyTo(email.replyTo());
            if (email.inReplyTo() != null) {
                message.setHeader("In-Reply-To", email.inReplyTo());
                message.setHeader("References", email.inReplyTo());
            }
            mailSender.send(message);
            log.info("Sent email '{}' to {}", email.subject(), email.to());
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new MailDeliveryException("Could not send email to " + email.to(), e);
        }
    }
}
