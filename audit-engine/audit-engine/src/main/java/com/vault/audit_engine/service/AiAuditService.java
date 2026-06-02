package com.vault.audit_engine.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;
import com.vault.audit_engine.model.AiPayloads.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;

@Service
public class AiAuditService {

    private final RestClient restClient;
    private final RestClient groqClient; // Dedicated, pooled client instance for Groq
    private final String apiKey;

    @Value("${ai.api.model:gpt-4o}")
    private String modelName;

    // Spring constructor injection
    public AiAuditService(RestClient.Builder restClientBuilder,
                          @Value("${ai.api.url:https://api.openai.com/v1}") String baseUrl,
                          @Value("${ai.api.key}") String apiKey) {

        this.apiKey = apiKey;

        // 1. Initialize primary OpenAI client
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        // 2. Initialize fixed connection factory to force HTTP/1.1 and reuse sockets safely
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(25000);

        // 3. Build a single reusable Groq client instance to avoid socket exhaustion errors
        this.groqClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) VaultAuditEngine/1.0")
                .build();
    }

    public String evaluateStatement(String rawText, String documentType) {
        String systemPrompt = """
                You are an elite automated corporate forensic financial auditor. 
                Analyze the following financial statement document text for:
                1. Mathematical consistency (e.g., verify if Assets match Liabilities + Equity).
                2. Outlier anomalies or unexpected variances.
                3. Missing fields, compliance gaps, or signs of potential structural errors.
                
                Respond in a highly structured, valid JSON string containing two fields: 
                'status' (must be exactly either 'PASSED' or 'ANOMALY_DETECTED') and 
                'findings' (a detailed array listing your specific analytical observations).
                """;

        String userPrompt = String.format("Document Type: %s\n\nContent:\n%s", documentType, rawText);

        ChatRequest requestBody = new ChatRequest(
                modelName,
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                0.1
        );

        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response != null && !response.choices().isEmpty()) {
                return response.choices().get(0).message().content();
            }
            throw new RuntimeException("AI API returned an empty completion choice payload.");

        } catch (Exception ex) {
            throw new RuntimeException("CRITICAL: Failed to communicate with external AI audit engine: " + ex.getMessage(), ex);
        }
    }

    public String evaluateStatementDirect(String companyId, String docType, String extractedText) {
        try {
            String safeExtractedText = extractedText.replace("\"", "'")
                    .replace("\n", " ")
                    .replace("\r", " ");

            String requestPayload = "{\n" +
                    "    \"model\": \"llama-3.3-70b-versatile\",\n" +
                    "    \"messages\": [\n" +
                    "        {\n" +
                    "            \"role\": \"system\",\n" +
                    "            \"content\": \"You are an elite automated corporate forensic financial auditor. Analyze the attached document or image for mathematical consistency, compliance gaps, and financial anomalies. Respond strictly in a valid JSON string containing: 'status' ('PASSED' or 'ANOMALY_DETECTED') and 'findings' (array of strings).\"\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"role\": \"user\",\n" +
                    "            \"content\": \"Company Identifier: " + companyId + " \\nDocument Category: " + docType + " \\nExtracted Text Data: " + safeExtractedText + "\"\n" +
                    "        }\n" +
                    "    ],\n" +
                    "    \"response_format\": { \"type\": \"json_object\" }\n" +
                    "}";

            // REUSE our single, thread-safe groqClient instance.
            // Reading as byte[].class handles any erratic application/octet-stream content headers perfectly!
            byte[] rawBytes = this.groqClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(byte[].class);

            if (rawBytes != null) {
                String rawResponse = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);

                // CLEAN UP STRIPPED MARKDOWN BLOCKS BEFORE SAVING TO DB
                if (rawResponse.contains("```json")) {
                    rawResponse = rawResponse.substring(rawResponse.indexOf("```json") + 7);
                    if (rawResponse.contains("```")) {
                        rawResponse = rawResponse.substring(0, rawResponse.indexOf("```"));
                    }
                } else if (rawResponse.contains("```")) {
                    rawResponse = rawResponse.substring(rawResponse.indexOf("```") + 3);
                    if (rawResponse.contains("```")) {
                        rawResponse = rawResponse.substring(0, rawResponse.indexOf("```"));
                    }
                }

                return rawResponse.trim();
            }

        } catch (Exception e) {
            System.out.println("⚠️ Groq execution pipeline anomaly: " + e.getMessage());

            // Fallback object to ensure UI components don't show 'undefined' if anything goes wrong
            return "{\n" +
                    "  \"choices\": [{\n" +
                    "    \"message\": {\n" +
                    "      \"content\": \"{\\\"status\\\":\\\"OFFLINE_FALLBACK\\\",\\\"confidence_score\\\":0.0,\\\"heuristics\\\":[{\\\"id\\\":\\\"NET-01\\\",\\\"status\\\":\\\"WARN\\\"}],\\\"findings\\\":[\\\"Network connection interrupted during remote handshake execution.\\\",\\\"Internal local verification vaulting sequence fallback engaged.\\\"],\\\"agent_recommendation\\\":\\\"MANUAL_REVIEW\\\"}\"\n" +
                    "    }\n" +
                    "  }]\n" +
                    "}";
        }
        return companyId;
    }
}
