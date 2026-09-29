package com.wdmmg.expense.mail;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.mail.inbound.ImapMailboxPoller;
import com.wdmmg.expense.mail.inbound.InboundEmail;
import com.wdmmg.expense.mail.inbound.InboundEmailRepository;
import com.wdmmg.expense.mail.inbound.InboundEmailService;
import com.wdmmg.expense.mail.inbound.InboundStatus;
import com.wdmmg.expense.mail.inbound.ReceivedEmail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Admin tools for checking the mail setup (under /api/admin → ROLE_ADMIN only). */
@RestController
@RequestMapping("/api/admin/mail")
public class MailAdminController {
    private final EmailSender sender;
    private final MailProperties props;
    private final InboundEmailRepository inbound;
    private final InboundEmailService inboundService;
    private final ObjectProvider<ImapMailboxPoller> poller;

    public MailAdminController(EmailSender sender, MailProperties props, InboundEmailRepository inbound,
                               InboundEmailService inboundService, ObjectProvider<ImapMailboxPoller> poller) {
        this.sender = sender;
        this.props = props;
        this.inbound = inbound;
        this.inboundService = inboundService;
        this.poller = poller;
    }

    public record TestRequest(@NotBlank @Email String to) {}

    public record SimulateRequest(@NotBlank @Email String from, @Size(max = 998) String subject,
                                  @NotBlank @Size(max = 20000) String text) {}

    public record InboundEmailResponse(Long id, String messageId, String fromAddress, String fromName, String subject,
                                       String bodyText, Instant receivedAt, Long userId, InboundStatus status,
                                       String handler, String result, Instant createdAt) {
        static InboundEmailResponse from(InboundEmail e) {
            return new InboundEmailResponse(e.getId(), e.getMessageId(), e.getFromAddress(), e.getFromName(), e.getSubject(),
                    e.getBodyText(), e.getReceivedAt(), e.getUserId(), e.getStatus(), e.getHandler(), e.getResult(), e.getCreatedAt());
        }
    }

    /** What's switched on, without leaking credentials. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "outboundEnabled", props.enabled(),
                "provider", props.enabled() ? props.provider() : "log",
                "from", props.from() == null ? "" : props.from(),
                "inboundEnabled", props.inbound() != null && props.inbound().enabled(),
                "inboundProvider", props.inbound() == null ? "imap" : props.inbound().provider(),
                "inboundMailbox", props.inbound() == null || props.inbound().username() == null ? "" : props.inbound().username(),
                "pollSeconds", props.inbound() == null ? 0 : props.inbound().pollSeconds());
    }

    /** Sends a test message – the quickest way to check SMTP credentials. */
    @PostMapping("/test")
    public Map<String, Object> test(@Valid @RequestBody TestRequest req) {
        try {
            sender.send(OutboundEmail.of(req.to(), "Test email from Where did my money go",
                    "If you can read this, outgoing email is set up correctly.", null));
        } catch (MailDeliveryException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Sending failed: " + cause.getMessage(), "MAIL_SEND_FAILED");
        }
        return Map.of("sent", true, "delivered", props.enabled(),
                "note", !props.enabled() ? "MAIL_ENABLED=false – written to the server log instead"
                        : MailProperties.RESEND.equals(props.provider()) ? "Accepted by Resend" : "Handed to the SMTP server");
    }

    @GetMapping("/inbound")
    public PageResponse<InboundEmailResponse> inbound(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        var p = inbound.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(p, InboundEmailResponse::from);
    }

    /** Check the mailbox now instead of waiting for the next scheduled poll. */
    @PostMapping("/inbound/poll")
    public Map<String, Object> pollNow() {
        ImapMailboxPoller p = poller.getIfAvailable();
        if (p == null) {
            boolean resend = props.inbound() != null && props.inbound().enabled()
                    && MailProperties.RESEND.equals(props.inbound().provider());
            throw ApiException.badRequest(resend
                    ? "Nothing to poll: incoming mail arrives from Resend webhooks (MAIL_INBOUND_PROVIDER=resend)"
                    : "Inbound mail is disabled (MAIL_INBOUND_ENABLED=false)");
        }
        return Map.of("processed", p.poll());
    }

    /** Push a message through the inbound pipeline without a mailbox – for testing handlers. */
    @PostMapping("/inbound/simulate")
    public InboundEmailResponse simulate(@Valid @RequestBody SimulateRequest req) {
        ReceivedEmail email = new ReceivedEmail("<simulated-" + UUID.randomUUID() + "@local>", req.from().trim().toLowerCase(),
                null, props.from(), req.subject(), req.text().strip(), Instant.now(), null, false);
        return InboundEmailResponse.from(inboundService.receive(email));
    }
}
