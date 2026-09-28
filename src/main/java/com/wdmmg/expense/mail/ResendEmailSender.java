package com.wdmmg.expense.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends through Resend's HTTPS API (api.resend.com, port 443) instead of SMTP, so it works on
 * hosts that block SMTP ports. Active when MAIL_ENABLED=true and MAIL_PROVIDER=resend.
 * MAIL_FROM must be an address on a domain verified in Resend.
 *
 * Rate limits (429) and server errors are retried twice with the same Idempotency-Key,
 * so a retry can never send the same email twice.
 */
@Component
@ConditionalOnExpression("${app.mail.enabled:false} and '${app.mail.provider:smtp}'.trim().equalsIgnoreCase('resend')")
public class ResendEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final int MAX_ATTEMPTS = 3;

    private final MailProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final String apiKey;

    public ResendEmailSender(MailProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        MailProperties.Resend cfg = props.resend();
        this.apiKey = cfg == null || cfg.apiKey() == null ? "" : cfg.apiKey().trim();
        if (apiKey.isEmpty()) {
            // Fail at startup rather than on the first sign-up.
            throw new IllegalStateException("MAIL_PROVIDER=resend needs RESEND_API_KEY (create one at resend.com → API Keys)");
        }
        String baseUrl = cfg.baseUrl() == null || cfg.baseUrl().isBlank() ? "https://api.resend.com" : cfg.baseUrl().replaceAll("/+$", "");
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        log.info("Outgoing email: Resend API ({}) as {}", baseUrl, props.from());
    }

    @Override
    public void send(OutboundEmail email) {
        String body = toJson(email);
        String idempotencyKey = UUID.randomUUID().toString();

        for (int attempt = 1; ; attempt++) {
            try {
                ResponseEntity<JsonNode> res = http.post()
                        .uri("/emails")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(JsonNode.class);
                String id = res.getBody() == null ? "?" : res.getBody().path("id").asText("?");
                log.info("Sent email '{}' to {} via Resend (id {})", email.subject(), email.to(), id);
                return;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw new MailDeliveryException("Resend rejected the email to " + email.to() + " (" + status + "): "
                            + resendMessage(e.getResponseBodyAsString()), null);
                }
                sleep(retryDelay(e, attempt));
            } catch (ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new MailDeliveryException("Couldn't reach Resend: " + e.getMessage(), e);
                }
                sleep(Duration.ofSeconds(attempt));
            }
        }
    }

    private String toJson(OutboundEmail email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", fromHeader());
        body.put("to", List.of(email.to()));
        body.put("subject", email.subject());
        body.put("text", email.text());
        if (email.html() != null) body.put("html", email.html());
        if (email.replyTo() != null) body.put("reply_to", email.replyTo());
        if (email.inReplyTo() != null) {
            // Keeps replies threaded in the recipient's mail client.
            body.put("headers", Map.of("In-Reply-To", email.inReplyTo(), "References", email.inReplyTo()));
        }
        try {
            // Serialised up front so the request has a Content-Length rather than a chunked body.
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new MailDeliveryException("Couldn't build the email request", e);
        }
    }

    /** "Where did my money go <hello@example.com>", quoting the name if it has special characters. */
    private String fromHeader() {
        String name = props.fromName() == null ? "" : props.fromName().trim();
        if (name.isEmpty()) return props.from();
        if (name.matches(".*[\",;:<>@()\\[\\]\\\\].*")) {
            name = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return name + " <" + props.from() + ">";
    }

    private String resendMessage(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            String msg = n.path("message").asText("");
            if (!msg.isBlank()) return msg;
        } catch (Exception ignored) {
            // not JSON
        }
        return body == null || body.isBlank() ? "no details" : (body.length() > 300 ? body.substring(0, 300) : body);
    }

    private static Duration retryDelay(HttpStatusCodeException e, int attempt) {
        String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Math.min(10, Math.max(1, Long.parseLong(retryAfter.trim()))));
            } catch (NumberFormatException ignored) {
                // HTTP-date form – fall back to backoff
            }
        }
        return Duration.ofSeconds(attempt); // 1 s, then 2 s
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new MailDeliveryException("Interrupted while retrying the email", ie);
        }
    }
}
