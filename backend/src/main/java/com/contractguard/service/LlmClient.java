package com.contractguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over an OpenAI-compatible chat completions endpoint.
 *
 * Retries transient failures with exponential backoff. This matters more than it
 * looks: analysing a contract fires one request per clause in a tight loop, and
 * free-tier providers rate-limit on tokens per minute as well as requests per
 * minute. Without retry, the later clauses of a long document quietly come back
 * unclassified.
 *
 * The distinction that makes retry correct rather than superstitious:
 *   - 429 and 5xx are TRANSIENT. The same request may well succeed shortly.
 *   - 400/401/404 are PERMANENT. Retrying a malformed request or a bad API key
 *     just wastes time and quota. Fail fast and say why.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MS = 800;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public LlmClient(RestClient llmRestClient,
                     ObjectMapper objectMapper,
                     @Value("${contractguard.llm.api-key}") String apiKey,
                     @Value("${contractguard.llm.model}") String model) {
        this.restClient = llmRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Sends a system + user prompt and returns the assistant's raw text reply.
     *
     * @throws LlmException when the provider fails permanently, or when every
     *                      retry of a transient failure is exhausted
     */
    public String complete(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("LLM_API_KEY is not set");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.1,   // low: we want consistent classification, not creativity
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        LlmException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callOnce(body);

            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();

                if (!isTransient(status)) {
                    // Permanent: a bad key or malformed request will fail
                    // identically every time. Retrying helps nobody.
                    throw new LlmException("provider returned HTTP " + status
                            + " (" + shortReason(status) + ")");
                }

                lastFailure = new LlmException("provider returned HTTP " + status);
                if (attempt < MAX_ATTEMPTS) {
                    backOff(attempt, retryAfterMs(ex), status);
                }

            } catch (ResourceAccessException ex) {
                // Connection reset, read timeout - transient by nature.
                lastFailure = new LlmException("network error: " + ex.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backOff(attempt, -1, 0);
                }
            }
        }

        log.error("LLM call failed after {} attempts: {}", MAX_ATTEMPTS, lastFailure.getMessage());
        throw lastFailure;
    }

    // ---------------------------------------------------------------- internals

    private String callOnce(Map<String, Object> body) {
        ResponseEntity<String> response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");

            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new LlmException("model returned an empty response");
            }
            return content.asText();

        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException("could not read provider response: " + ex.getMessage());
        }
    }

    /** 429 = rate limited, 5xx = provider-side. Both are worth another go. */
    private boolean isTransient(int status) {
        return status == 429 || status >= 500;
    }

    /**
     * Providers often tell you exactly how long to wait via the Retry-After
     * header. Honouring it is politer and more effective than guessing.
     */
    private long retryAfterMs(RestClientResponseException ex) {
        try {
            String header = ex.getResponseHeaders() == null
                    ? null
                    : ex.getResponseHeaders().getFirst("Retry-After");
            if (header == null || header.isBlank()) {
                return -1;
            }
            return Math.round(Double.parseDouble(header.trim()) * 1000);
        } catch (NumberFormatException ex2) {
            return -1;   // header was an HTTP-date, not seconds; fall back to backoff
        }
    }

    private void backOff(int attempt, long retryAfterMs, int status) {
        // Exponential: 800ms, 1.6s, 3.2s - unless the provider named a delay.
        long waitMs = retryAfterMs > 0
                ? retryAfterMs
                : BASE_BACKOFF_MS * (1L << (attempt - 1));

        log.warn("LLM attempt {}/{} failed{}, retrying in {}ms",
                attempt, MAX_ATTEMPTS,
                status > 0 ? " (HTTP " + status + ")" : "",
                waitMs);

        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ex) {
            // Restore the flag rather than swallowing it. Code higher up the
            // stack needs to be able to see that this thread was interrupted --
            // swallowing InterruptedException is a well-known Java bug pattern.
            Thread.currentThread().interrupt();
            throw new LlmException("interrupted while backing off");
        }
    }

    private String shortReason(int status) {
        return switch (status) {
            case 400 -> "bad request";
            case 401 -> "invalid API key";
            case 403 -> "forbidden";
            case 404 -> "model not found - check LLM_MODEL";
            case 413 -> "payload too large";
            default  -> "client error";
        };
    }

    /** Unchecked so it can propagate to the per-clause handler without noise. */
    public static class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }
    }
}
