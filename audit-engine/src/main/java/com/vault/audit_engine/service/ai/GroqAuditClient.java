package com.vault.audit_engine.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ModelAssessment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Groq's OpenAI-compatible chat completions API. Text documents go to the text model;
 * scanned PDFs and photos go to the vision model as images.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq", matchIfMissing = true)
public class GroqAuditClient implements AuditModelClient {

    private static final Logger logger = LoggerFactory.getLogger(GroqAuditClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String textModel;
    private final String visionModel;
    private final int maxAttempts;
    private final Duration retryDelay;

    // Never wait longer than this between attempts, whatever the provider asks for
    private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(30);

    // Timeouts come from spring.http.client.* in application.properties
    public GroqAuditClient(RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper,
                           @Value("${ai.api.url}") String baseUrl,
                           @Value("${ai.api.key}") String apiKey,
                           @Value("${ai.api.model}") String textModel,
                           @Value("${ai.api.vision-model}") String visionModel,
                           @Value("${ai.retry.max-attempts:3}") int maxAttempts,
                           @Value("${ai.retry.delay:2s}") Duration retryDelay) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.textModel = textModel;
        this.visionModel = visionModel;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = retryDelay;
    }

    @Override
    public ModelAssessment assess(String documentType, ExtractedDocument document) {
        String model = document.hasImages() ? visionModel : textModel;
        ChatRequest request = new ChatRequest(
                model,
                List.of(new ChatMessage("system", SYSTEM_PROMPT),
                        new ChatMessage("user", userContent(documentType, document))),
                0.0,
                new ResponseFormat("json_object"));

        ChatResponse response = send(request);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new AuditModelException("The AI provider returned an empty response.");
        }
        return parseAssessment(response.choices().get(0).message().content()).withModel(model);
    }

    /** Sends the request, waiting and retrying when the provider is rate-limiting (429) or briefly overloaded (503). */
    private ChatResponse send(ChatRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(ChatResponse.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status != 429 && status != 503) || attempt >= maxAttempts) {
                    throw new AuditModelException(describeHttpError(e), e);
                }
                Duration wait = waitBeforeRetry(e, attempt);
                logger.info("AI provider returned {}; retrying in {} ms (attempt {} of {})",
                        status, wait.toMillis(), attempt + 1, maxAttempts);
                sleep(wait);
            } catch (RestClientException e) {
                throw new AuditModelException("Could not reach the AI provider: " + e.getMessage(), e);
            }
        }
    }

    /** Uses the provider's Retry-After (in seconds) when it sends one, otherwise waits a little longer each time. */
    private Duration waitBeforeRetry(RestClientResponseException e, int attempt) {
        Duration wait = retryDelay.multipliedBy(attempt);
        String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                wait = Duration.ofMillis(Math.round(Double.parseDouble(retryAfter.trim()) * 1000));
            } catch (NumberFormatException ignored) {
                // An HTTP date instead of seconds: keep our own delay
            }
        }
        return wait.compareTo(MAX_RETRY_WAIT) > 0 ? MAX_RETRY_WAIT : wait;
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuditModelException("Interrupted while waiting to retry the AI provider.", e);
        }
    }

    private Object userContent(String documentType, ExtractedDocument document) {
        StringBuilder header = new StringBuilder()
                .append("Document type: ").append(documentType).append('\n')
                .append("File: ").append(document.sourceName()).append('\n')
                .append(AuditModelClient.amountInstructions(documentType));
        if (document.truncated()) {
            header.append("Note: the document was cut short to fit size limits; mention this if it affects the review.\n");
        }

        if (!document.hasImages()) {
            return header + "\n<document>\n" + document.text() + "\n</document>";
        }

        // Vision request: one text part, then each page as a base64 data URL
        List<ContentPart> parts = new ArrayList<>();
        parts.add(ContentPart.text(header + "The document pages follow as images."));
        for (ExtractedDocument.DocumentImage image : document.images()) {
            parts.add(ContentPart.image("data:" + image.mediaType() + ";base64," + image.base64Data()));
        }
        return parts;
    }

    ModelAssessment parseAssessment(String content) {
        if (content == null || content.isBlank()) {
            throw new AuditModelException("The AI model returned no content.");
        }
        String json = content.strip();
        // Some models wrap JSON in a markdown code fence despite JSON mode
        if (json.startsWith("```")) {
            json = json.substring(json.indexOf('\n') + 1);
            int fence = json.lastIndexOf("```");
            if (fence >= 0) {
                json = json.substring(0, fence);
            }
        }
        try {
            return objectMapper.readValue(json, ModelAssessment.class);
        } catch (JsonProcessingException e) {
            throw new AuditModelException("The AI model's answer was not the expected JSON.", e);
        }
    }

    private static String describeHttpError(RestClientResponseException e) {
        return switch (e.getStatusCode().value()) {
            case 401 -> "The AI provider rejected the API key (401). Check AI_API_KEY.";
            case 413 -> "The document is too large for the AI provider (413).";
            case 429 -> "The AI provider's rate limit was still reached after retrying (429). Try again in a minute.";
            default -> "The AI provider returned HTTP " + e.getStatusCode().value() + ".";
        };
    }

    // OpenAI-compatible request/response shapes

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ChatRequest(String model, List<ChatMessage> messages, double temperature, ResponseFormat responseFormat) {}

    record ChatMessage(String role, Object content) {}

    record ResponseFormat(String type) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ContentPart(String type, String text, ImageUrl imageUrl) {
        static ContentPart text(String text) { return new ContentPart("text", text, null); }
        static ContentPart image(String url) { return new ContentPart("image_url", null, new ImageUrl(url)); }
    }

    record ImageUrl(String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(ResponseMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponseMessage(String content) {}
}
