package com.priceintel.backend.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.priceintel.backend.constants.AiProvider;
import com.priceintel.backend.exception.AiException;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI implementation of the {@link AiGateway}, calling the Chat Completions
 * API. Handles auth, JSON-only response format, retry with backoff, timeouts
 * (via the RestClient), and extracts token usage.
 */
@Slf4j
@Component
public class OpenAiGateway implements AiGateway {

    private final OpenAiProperties props;
    private final RestClient openAiRestClient;
    private final com.priceintel.backend.marketplace.amazon.SimpleRateLimiter rateLimiter;

    private static final long BASE_BACKOFF_MS = 500;

    /** First wait after a 429, doubling per attempt. */
    private static final long RATE_LIMIT_BACKOFF_MS = 8_000;

    public OpenAiGateway(OpenAiProperties props,
                         @Qualifier("openAiRestClient") RestClient openAiRestClient) {
        this.props = props;
        this.openAiRestClient = openAiRestClient;
        // Paced to the provider's quota rather than left to the retry. Free
        // tiers meter by the minute, and a 500ms backoff cannot outwait a
        // minute-wide window — the retries just burn through and fail.
        this.rateLimiter = new com.priceintel.backend.marketplace.amazon.SimpleRateLimiter(
                Math.max(props.getRequestsPerMinute(), 1) / 60.0);
    }

    @Override
    public boolean isAvailable() {
        return props.isConfigured();
    }

    @Override
    public AiCompletion complete(AiCompletionRequest request) {
        if (!props.isConfigured()) {
            throw new AiException("OpenAI is not configured. Set openai.enabled=true and OPENAI_API_KEY.");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            // Multimodal form: content becomes a list of parts rather than a
            // string. Only sent when there is an image, because models that
            // accept only text reject the list form outright with a 400.
            Map<String, Object> imagePart = new java.util.HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", request.getImageUrl()));

            messages.add(Map.of("role", "user", "content", List.of(
                    Map.of("type", "text", "text", request.getUserPrompt()),
                    imagePart)));
        } else {
            messages.add(Map.of("role", "user", "content", request.getUserPrompt()));
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel() : props.getModel());
        body.put("messages", messages);
        body.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0);
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.isJsonOnly()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        JsonNode response = executeWithRetry(body);
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        JsonNode usage = response.path("usage");

        return AiCompletion.builder()
                .content(content)
                // The real provider, not the protocol's name: Groq and a local
                // Ollama both answer on this API, and recording all three as
                // OPENAI would make the audit log unable to explain a change in
                // verdict quality after a provider switch.
                .provider(props.provider())
                .model(response.path("model").asText(props.getModel()))
                .promptTokens(usage.path("prompt_tokens").isNumber() ? usage.get("prompt_tokens").asInt() : null)
                .completionTokens(usage.path("completion_tokens").isNumber()
                        ? usage.get("completion_tokens").asInt() : null)
                .totalTokens(usage.path("total_tokens").isNumber() ? usage.get("total_tokens").asInt() : null)
                .build();
    }

    private JsonNode executeWithRetry(Map<String, Object> body) {
        int attempt = 0;
        while (true) {
            attempt++;
            rateLimiter.acquire();
            long start = System.currentTimeMillis();
            try {
                JsonNode resp = openAiRestClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                log.info("OpenAI completion succeeded in {}ms (attempt {})",
                        System.currentTimeMillis() - start, attempt);
                if (resp == null) {
                    throw new AiException("OpenAI returned an empty response");
                }
                return resp;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                String errorBody = e.getResponseBodyAsString();
                // A reply cut short by an exhausted per-minute token budget
                // arrives as 400 json_validate_failed, not 429 — a bad-request
                // code for a rate problem. Treated as permanent it fails the
                // whole judging pass; waited out like the rate limit it is, the
                // next attempt succeeds.
                boolean starvedByQuota = status == 400 && errorBody != null
                        && errorBody.contains("json_validate_failed");
                boolean transientError = status == 429 || status >= 500 || starvedByQuota;
                // The provider's own message, not just the code. A bare "HTTP
                // 400" is unactionable — it covers a decommissioned model, an
                // oversized request and a reply that failed JSON validation,
                // which need three different fixes. The body says which.
                log.warn("{} HTTP {} (attempt {}): {}", props.provider(), status, attempt,
                        abbreviate(errorBody));
                if (transientError && attempt <= props.getMaxRetries()) {
                    // A quota exhausted for the minute cannot be outwaited in half
                    // a second: the window is a minute wide, so the backoff has to
                    // be measured on that scale. Server errors keep the short one.
                    if (status == 429 || starvedByQuota) {
                        rateLimitBackoff(attempt);
                    } else {
                        backoff(attempt);
                    }
                    continue;
                }
                throw new AiException(
                        props.provider() + " call failed with HTTP " + status,
                        AiException.reasonForStatus(status), e);
            } catch (ResourceAccessException e) {
                log.warn("OpenAI connection/timeout (attempt {}): {}", attempt, e.getMessage());
                if (attempt <= props.getMaxRetries()) {
                    backoff(attempt);
                    continue;
                }
                throw new AiException("OpenAI call timed out / unreachable", e);
            }
        }
    }

    /** Enough of an error body to identify it, without filling the log. */
    private String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "(no response body)";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 600 ? flat : flat.substring(0, 600) + "…";
    }

    private void backoff(int attempt) {
        sleep(BASE_BACKOFF_MS * (1L << (attempt - 1)));
    }

    /**
     * The wait after a rate limit.
     *
     * <p>Seconds, not milliseconds: providers meter tokens per minute, so the
     * quota that refused this call refills over that minute. Retrying half a
     * second later spends an attempt to be refused again, and by the third one
     * the caller has a failure instead of an answer.</p>
     */
    private void rateLimitBackoff(int attempt) {
        long delayMs = RATE_LIMIT_BACKOFF_MS * attempt;
        log.info("{} rate-limited — waiting {}s before attempt {}",
                props.provider(), delayMs / 1000, attempt + 1);
        sleep(delayMs);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
