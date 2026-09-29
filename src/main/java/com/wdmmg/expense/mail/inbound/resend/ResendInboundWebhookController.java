package com.wdmmg.expense.mail.inbound.resend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wdmmg.expense.mail.MailProperties;
import com.wdmmg.expense.mail.inbound.InboundEmail;
import com.wdmmg.expense.mail.inbound.InboundEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Receives Resend's "email.received" webhooks (MAIL_INBOUND_PROVIDER=resend). Each delivery is
 * verified by its signature, the full email is fetched from Resend, and it goes through the same
 * pipeline as mailbox mail. Being an incoming request, it also wakes a sleeping free-tier instance.
 *
 * Replies: 2xx = done (including duplicates and ignored events); 401 = bad signature (Resend
 * won't fix that by retrying, but it's surfaced in its dashboard); 502/503 = temporary problem,
 * so Resend retries later – safe because processing is idempotent by Message-ID.
 */
@RestController
@RequestMapping("/api/inbound")
@ConditionalOnExpression("${app.mail.inbound.enabled:false} and '${app.mail.inbound.provider:imap}'.trim().equalsIgnoreCase('resend')")
public class ResendInboundWebhookController {
    private static final Logger log = LoggerFactory.getLogger(ResendInboundWebhookController.class);

    private final SvixSignature signature;
    private final ResendInboundClient resend;
    private final InboundEmailService inbound;
    private final ObjectMapper mapper;

    public ResendInboundWebhookController(MailProperties props, InboundEmailService inbound, ObjectMapper mapper) {
        String secret = props.inbound() == null ? null : props.inbound().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("MAIL_INBOUND_PROVIDER=resend needs RESEND_WEBHOOK_SECRET (the whsec_… signing secret "
                    + "shown on the webhook in Resend → Webhooks)");
        }
        this.signature = new SvixSignature(secret);
        this.resend = new ResendInboundClient(props);
        if (!resend.configured()) {
            throw new IllegalStateException("MAIL_INBOUND_PROVIDER=resend needs RESEND_API_KEY to fetch received emails");
        }
        this.inbound = inbound;
        this.mapper = mapper;
        log.info("Incoming email: Resend webhooks at POST /api/inbound/resend");
    }

    @PostMapping("/resend")
    public ResponseEntity<Map<String, Object>> receive(@RequestHeader(value = "svix-id", required = false) String id,
                                                       @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
                                                       @RequestHeader(value = "svix-signature", required = false) String sig,
                                                       @RequestBody byte[] body) {
        SvixSignature.Result check = signature.verify(id, timestamp, sig, body, Instant.now());
        if (check != SvixSignature.Result.VALID) {
            log.warn("Rejected Resend webhook {}: {}", id, check);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid signature", "reason", check.name()));
        }

        JsonNode event;
        try {
            event = mapper.readTree(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "malformed JSON"));
        }
        String type = event.path("type").asText("");
        if (!"email.received".equals(type)) {
            return ResponseEntity.ok(Map.of("ignored", type)); // other event types subscribed by mistake
        }
        String emailId = event.path("data").path("email_id").asText("");
        if (emailId.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "missing data.email_id"));

        JsonNode email;
        try {
            email = resend.fetch(emailId);
        } catch (ResendInboundClient.FetchException e) {
            log.error("Couldn't fetch received email {} from Resend: {}", emailId, e.getMessage());
            // 401/403 = key can't read received mail (needs Full access); still ask for a retry
            // so nothing is lost once the key is fixed.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "could not fetch email", "status", e.status));
        }

        InboundEmail rec = inbound.receive(ResendEmailMapper.map(email));
        if (rec == null) return ResponseEntity.ok(Map.of("duplicate", true));
        return ResponseEntity.ok(Map.of("status", rec.getStatus().name()));
    }
}
