package com.wdmmg.expense.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for the Gemini API (generativelanguage.googleapis.com, generateContent).
 * Every call asks for JSON matching a response schema, so callers get structured data
 * rather than prose to parse.
 */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    public GeminiClient(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder()
                .baseUrl(baseUrl(props))
                .requestFactory(factory)
                .build();
    }

    /** Gemini is selected (AI_PROVIDER=gemini) and has a key. */
    public boolean isConfigured() {
        return "gemini".equalsIgnoreCase(props.provider()) && props.gemini() != null
                && props.gemini().apiKey() != null && !props.gemini().apiKey().isBlank();
    }

    public String model() {
        String m = props.gemini() == null ? null : props.gemini().model();
        return m == null || m.isBlank() ? "gemini-2.5-flash" : m.trim();
    }

    /**
     * @param system       instructions (kept separate from untrusted content)
     * @param user         the content to work on
     * @param responseSchema OpenAPI-style schema the reply must follow
     */
    public <T> T generateJson(String system, String user, Map<String, Object> responseSchema, Class<T> type) {
        if (!isConfigured()) throw new AiException("Gemini isn't configured (set AI_PROVIDER=gemini and GEMINI_API_KEY)");
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));
        String json;
        try {
            // Serialised up front so the request carries a Content-Length rather than a chunked body.
            json = mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiException("Couldn't build the Gemini request", e);
        }
        long started = System.nanoTime();
        JsonNode reply;
        try {
            reply = http.post()
                    .uri("/models/{model}:generateContent", model())
                    .header("x-goog-api-key", props.gemini().apiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String detail = new String(res.getBody().readAllBytes());
                        throw new AiException("Gemini returned " + res.getStatusCode().value() + ": " + errorMessage(detail));
                    })
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AiException("Couldn't reach Gemini: " + e.getMessage(), e);
        }
        String text = extractText(reply);
        log.debug("Gemini {} answered in {} ms", model(), Duration.ofNanos(System.nanoTime() - started).toMillis());
        try {
            return mapper.readValue(stripFences(text), type);
        } catch (Exception e) {
            throw new AiException("Gemini returned output that doesn't match the expected format", e);
        }
    }

    private String extractText(JsonNode reply) {
        if (reply == null) throw new AiException("Empty response from Gemini");
        JsonNode blocked = reply.path("promptFeedback").path("blockReason");
        if (!blocked.isMissingNode() && !blocked.asText().isBlank()) {
            throw new AiException("Gemini declined the request (" + blocked.asText() + ")");
        }
        JsonNode candidate = reply.path("candidates").path(0);
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue; // skip reasoning summaries
            sb.append(part.path("text").asText(""));
        }
        if (sb.isEmpty()) {
            throw new AiException("Gemini returned no content (finish reason: " + candidate.path("finishReason").asText("unknown") + ")");
        }
        return sb.toString();
    }

    private String errorMessage(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            String msg = n.path("error").path("message").asText("");
            if (!msg.isBlank()) return msg;
        } catch (Exception ignored) {
            // not JSON
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static String stripFences(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "");
            t = t.replaceFirst("\\s*```$", "");
        }
        return t;
    }

    private static String baseUrl(AiProperties p) {
        String u = p.gemini() == null ? null : p.gemini().baseUrl();
        return u == null || u.isBlank() ? "https://generativelanguage.googleapis.com/v1beta" : u.replaceAll("/+$", "");
    }
}
