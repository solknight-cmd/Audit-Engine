package com.vault.audit_engine;

import com.vault.audit_engine.controller.AuditIngestionController.IngestionRequest;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.repository.AuditRecordRepository;
import com.vault.audit_engine.service.AiAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditVaultIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @MockitoBean
    private AiAuditService aiAuditService; // Mock the external AI API dependency

    @Test
    void testAsynchronousIngestionToVaultPipeline() {
        // Arrange
        String companyId = "COMP-9482";
        String docType = "BALANCE_SHEET";
        String rawText = "Assets: 100, Liabilities: 40, Equity: 60";
        String mockAiResponse = "{\"status\":\"PASSED\",\"findings\":[\"Calculations balance perfectly.\"]}";

        // Mock our AI client response so we don't spend real API credits during a build
        Mockito.when(aiAuditService.evaluateStatement(rawText, docType)).thenReturn(mockAiResponse);

        IngestionRequest payload = new IngestionRequest(companyId, docType, rawText);
        String url = "http://localhost:" + port + "/api/v1/audits/ingest";

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(url, payload, String.class);

        // Assert Step 1: Endpoint must return an immediate HTTP 202 Accepted acknowledgement
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        // Assert Step 2: Use Awaitility to safely poll the database until the background thread saves the vault entry
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditRecord> records = auditRecordRepository.findAll();

            // Confirm exactly one immutable record was safely generated and appended
            assertEquals(1, records.size());

            AuditRecord vaultedRecord = records.get(0);
            assertEquals(companyId, vaultedRecord.getCompanyId());
            assertEquals("PASSED", vaultedRecord.getAuditStatus());
            assertEquals(mockAiResponse, vaultedRecord.getAiAuditReport());
        });
    }
}
