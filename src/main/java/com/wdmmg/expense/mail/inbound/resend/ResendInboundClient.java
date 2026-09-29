package com.wdmmg.expense.mail.inbound.resend;

import com.fasterxml.jackson.databind.JsonNode;
import com.wdmmg.expense.mail.MailProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/** Fetches a received email (body, headers, SPF/DKIM/DMARC) – webhooks only carry its id. */
class ResendInboundClient {
    private final RestClient http;
    private final String apiKey;

    ResendInboundClient(MailProperties props) {
        MailProperties.Resend cfg = props.resend();
        this.apiKey = cfg == null || cfg.apiKey() == null ? "" : cfg.apiKey().trim();
        String baseUrl = cfg == null || cfg.baseUrl() == null || cfg.baseUrl().isBlank()
                ? "https://api.resend.com" : cfg.baseUrl().replaceAll("/+$", "");
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    boolean configured() {
        return !apiKey.isEmpty();
    }

    /** @throws FetchException with the HTTP status (0 = network) so the caller can decide on retries */
    JsonNode fetch(String emailId) {
        try {
            return http.get()
                    .uri("/emails/receiving/{id}", emailId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw new FetchException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new FetchException(0, e.getMessage());
        }
    }

    static final class FetchException extends RuntimeException {
        final int status;

        FetchException(int status, String detail) {
            super("Resend returned " + (status == 0 ? "no response" : status) + ": " + detail);
            this.status = status;
        }
    }
}
