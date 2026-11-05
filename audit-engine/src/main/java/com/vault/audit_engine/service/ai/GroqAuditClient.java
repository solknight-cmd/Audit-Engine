package com.vault.audit_engine.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ModelAssessment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Groq's OpenAI-compatible chat completions API. Text documents go to the text model;
 * scanned PDFs and photos go to the vision model as images.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq", matchIfMissing = true)
public class GroqAuditClient implements AuditModelClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String textModel;
    private final String visionModel;

    // Timeouts come from spring.http.client.* in application.properties
    public GroqAuditClient(RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper,
                           @Value("${ai.api.url}") String baseUrl,
                           @Value("${ai.api.key}") String apiKey,
                           @Value("${ai.api.model}") String textModel,
                           @Value("${ai.api.vision-model}") String visionModel) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.textModel = textModel;
        this.visionModel = visionModel;
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

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            throw new AuditModelException(describeHttpError(e), e);
        } catch (RestClientException e) {
            throw new AuditModelException("Could not reach the AI provider: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new AuditModelException("The AI provider returned an empty response.");
        }
        return parseAssessment(response.choices().get(0).message().content()).withModel(model);
    }

    private Object userContent(String documentType, ExtractedDocument document) {
        StringBuilder header = new StringBuilder()
                .append("Document type: ").append(documentType).append('\n')
                .append("File: ").append(document.sourceName()).append('\n');
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
            case 429 -> "The AI provider's rate limit was reached (429). Try again in a minute.";
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
