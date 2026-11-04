package com.vault.audit_engine;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ModelAssessment;
import com.vault.audit_engine.model.StatementFigures;
import com.vault.audit_engine.repository.AuditRecordRepository;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import com.vault.audit_engine.service.ai.AuditModelClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditVaultIntegrationTest {

    // Both tenants are created by the demo seed on startup
    private static final String GLOBAL = "GLOBAL_CAPITAL_CORP_001";
    private static final String VANGUARD = "VANGUARD_EQUITIES_LTD";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Autowired
    private CompanyMetricsRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuditModelClient modelClient; // Mock the external AI API dependency

    private static VaultUserDetails auditorAt(String tenantId) {
        return new VaultUserDetails("auditor@" + tenantId, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR")), tenantId, "Test Auditor");
    }

    private static ModelAssessment passingAssessment(String assets, String liabilities, String equity) {
        return new ModelAssessment(new StatementFigures(new BigDecimal(assets), new BigDecimal(liabilities),
                new BigDecimal(equity), null, null, null), "PASSED", List.of(), "mock-model");
    }

    @Test
    void ingestVaultsVerifiedReportUnderCallersTenantAndUpdatesMetrics() throws Exception {
        String rawText = "Total Assets: $1,000,000 Total Liabilities: $400,000 Shareholders Equity: $600,000";

        // Mock our AI client response so we don't spend real API credits during a build
        Mockito.when(modelClient.assess(eq("BALANCE_SHEET"), any()))
                .thenReturn(passingAssessment("1000000", "400000", "600000"));

        // Endpoint must return an immediate HTTP 202 Accepted acknowledgement
        mockMvc.perform(post("/api/v1/audits/ingest")
                        .with(user(auditorAt(GLOBAL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"BALANCE_SHEET\",\"rawText\":\"" + rawText + "\"}"))
                .andExpect(status().isAccepted());

        // The background thread saves the vault entry, then the metrics listener runs
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditRecord> records = auditRecordRepository.findByCompanyIdOrderByVaultedAtDesc(GLOBAL);
            assertEquals(1, records.size());
            AuditRecord record = records.get(0);
            assertEquals("PASSED", record.getAuditStatus());
            assertEquals("api-ingest", record.getSourceName());
            assertEquals(64, record.getSourceSha256().length());
            assertTrue(record.getAiAuditReport().contains("\"figureCheck\":\"CHECKED_AGAINST_TEXT\""));

            var metrics = metricsRepository.findByCompanyId(GLOBAL).orElseThrow();
            assertEquals(1, metrics.getTotalAuditsProcessed());
            assertEquals("1,000,000.00", metrics.getTotalAssets());
            assertEquals("PASSED", metrics.getLastAuditStatus());
        });
    }

    @Test
    void uploadedImageIsSentToTheModelAsAnImage() throws Exception {
        Mockito.when(modelClient.assess(eq("SCANNED_RECEIPT"), any()))
                .thenReturn(passingAssessment("100", "40", "60"));
        MockMultipartFile image = new MockMultipartFile("file", "statement.PNG", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/audits/upload").file(image)
                        .param("documentType", "SCANNED_RECEIPT")
                        .with(user(auditorAt(VANGUARD))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<ExtractedDocument> sent = ArgumentCaptor.forClass(ExtractedDocument.class);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                Mockito.verify(modelClient).assess(eq("SCANNED_RECEIPT"), sent.capture()));
        assertEquals("IMAGE", sent.getValue().method());
        assertEquals(1, sent.getValue().images().size());
    }

    @Test
    void unsupportedUploadIsRejectedWithAReason() throws Exception {
        MockMultipartFile doc = new MockMultipartFile("file", "notes.docx", null, new byte[]{1});

        mockMvc.perform(multipart("/api/v1/audits/upload").file(doc)
                        .param("documentType", "BALANCE_SHEET")
                        .with(user(auditorAt(GLOBAL))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unsupported file format")));
    }

    @Test
    void vaultOnlyReturnsCallersOwnTenant() throws Exception {
        auditRecordRepository.save(new AuditRecord(VANGUARD, "BALANCE_SHEET", "text", "{}", "PASSED", "a.pdf", null));

        // A Global Capital auditor must not see Vanguard's record
        mockMvc.perform(get("/api/v1/audits/vault").with(user(auditorAt(GLOBAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.companyId == '" + VANGUARD + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/audits/vault").with(user(auditorAt(VANGUARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyId").value(VANGUARD));
    }

    @Test
    void apiRejectsRequestsWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/audits/vault"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRejectsChangesToVaultedRecords() {
        // Vanguard, so this doesn't change the Global record count asserted in the ingest test
        AuditRecord saved = auditRecordRepository.save(
                new AuditRecord(VANGUARD, "BALANCE_SHEET", "text", "{}", "PASSED", "a.pdf", null));

        // Bypasses JPA entirely, so this is the database trigger doing the blocking
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "UPDATE financial_audit_vault SET audit_status = 'PASSED_TAMPERED' WHERE id = ?", saved.getId()));
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "DELETE FROM financial_audit_vault WHERE id = ?", saved.getId()));
        assertThrows(Exception.class, () -> jdbcTemplate.execute("TRUNCATE financial_audit_vault"));
    }
}
