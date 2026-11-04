package com.vault.audit_engine;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.repository.AuditRecordRepository;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import com.vault.audit_engine.service.AiAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private AiAuditService aiAuditService; // Mock the external AI API dependency

    private static VaultUserDetails auditorAt(String tenantId) {
        return new VaultUserDetails("auditor@" + tenantId, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR")), tenantId, "Test Auditor");
    }

    @Test
    void ingestVaultsRecordUnderCallersTenantAndUpdatesMetrics() throws Exception {
        String docType = "BALANCE_SHEET";
        String rawText = "Total Assets: $1,000,000 Total Liabilities: $400,000 Shareholders Equity: $600,000";
        String mockAiResponse = "{\"status\":\"PASSED\",\"findings\":[\"Calculations balance perfectly.\"]}";

        // Mock our AI client response so we don't spend real API credits during a build
        Mockito.when(aiAuditService.evaluateStatement(rawText, docType)).thenReturn(mockAiResponse);

        // Endpoint must return an immediate HTTP 202 Accepted acknowledgement
        mockMvc.perform(post("/api/v1/audits/ingest")
                        .with(user(auditorAt(GLOBAL)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"" + docType + "\",\"rawText\":\"" + rawText + "\"}"))
                .andExpect(status().isAccepted());

        // The background thread saves the vault entry, then the metrics listener runs
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditRecord> records = auditRecordRepository.findByCompanyIdOrderByVaultedAtDesc(GLOBAL);
            assertEquals(1, records.size());
            assertEquals("PASSED", records.get(0).getAuditStatus());
            assertEquals(mockAiResponse, records.get(0).getAiAuditReport());

            var metrics = metricsRepository.findByCompanyId(GLOBAL).orElseThrow();
            assertEquals(1, metrics.getTotalAuditsProcessed());
            assertEquals("$1,000,000.00", metrics.getTotalAssets());
            assertEquals("PASSED", metrics.getLastAuditStatus());
        });
    }

    @Test
    void vaultOnlyReturnsCallersOwnTenant() throws Exception {
        auditRecordRepository.save(new AuditRecord(VANGUARD, "BALANCE_SHEET", "text", "{}", "PASSED"));

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
                new AuditRecord(VANGUARD, "BALANCE_SHEET", "text", "{}", "PASSED"));

        // Bypasses JPA entirely, so this is the database trigger doing the blocking
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "UPDATE financial_audit_vault SET audit_status = 'PASSED_TAMPERED' WHERE id = ?", saved.getId()));
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "DELETE FROM financial_audit_vault WHERE id = ?", saved.getId()));
        assertThrows(Exception.class, () -> jdbcTemplate.execute("TRUNCATE financial_audit_vault"));
    }
}
