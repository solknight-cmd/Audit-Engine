package com.vault.audit_engine.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ExtractedDocument.DocumentImage;
import com.vault.audit_engine.model.ModelAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqAuditClientTest {

    private MockRestServiceServer server;
    private GroqAuditClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GroqAuditClient(builder, objectMapper, "https://groq.test/v1", "test-key", "text-model", "vision-model");
    }

    private static String completion(String content) throws Exception {
        String escaped = new ObjectMapper().writeValueAsString(content);
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + escaped + "}}]}";
    }

    private static final String ASSESSMENT = """
            {"figures":{"total_assets":1250000,"total_liabilities":500000,"total_equity":750000,
             "currency":"NGN","period":"31 Dec 2025","scale":"thousands"},
             "status":"PASSED","findings":[{"severity":"LOW","description":"No comparatives"}]}
            """;

    @Test
    void textDocumentGoesToTextModelInJsonMode() throws Exception {
        server.expect(requestTo("https://groq.test/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("text-model"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Total assets 1,250,000")))
                .andRespond(withSuccess(completion(ASSESSMENT), MediaType.APPLICATION_JSON));

        ModelAssessment result = client.assess("BALANCE_SHEET", new ExtractedDocument(
                "bs.pdf", "abc", "Total assets 1,250,000", List.of(), "PDF_TEXT", false));

        assertEquals(new BigDecimal("1250000"), result.figures().totalAssets());
        assertEquals("thousands", result.figures().scale());
        assertEquals("PASSED", result.status());
        assertEquals(1, result.findings().size());
        assertEquals("text-model", result.model());
        server.verify();
    }

    @Test
    void scannedDocumentGoesToVisionModelAsDataUrls() throws Exception {
        server.expect(requestTo("https://groq.test/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("vision-model"))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").value("data:image/jpeg;base64,AAAA"))
                .andRespond(withSuccess(completion("```json\n" + ASSESSMENT + "\n```"), MediaType.APPLICATION_JSON));

        ModelAssessment result = client.assess("BALANCE_SHEET", new ExtractedDocument(
                "scan.pdf", "abc", "[Scanned PDF]", List.of(new DocumentImage("image/jpeg", "AAAA")), "PDF_SCANNED", false));

        assertEquals("vision-model", result.model());
        assertEquals(new BigDecimal("750000"), result.figures().totalEquity());
        server.verify();
    }

    @Test
    void rateLimitBecomesReadableError() {
        server.expect(requestTo("https://groq.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        AuditModelException e = assertThrows(AuditModelException.class, () -> client.assess("BALANCE_SHEET",
                new ExtractedDocument("bs.pdf", "abc", "text", List.of(), "PDF_TEXT", false)));
        assertTrue(e.getMessage().contains("rate limit"));
    }

    @Test
    void nonJsonAnswerIsRejected() {
        assertThrows(AuditModelException.class, () -> client.parseAssessment("Looks fine to me!"));
    }
}
