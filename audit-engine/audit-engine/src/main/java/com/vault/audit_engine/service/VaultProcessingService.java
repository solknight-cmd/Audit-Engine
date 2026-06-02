package com.vault.audit_engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.audit_engine.model.AuditCompletedEvent;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.repository.AuditRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class VaultProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(VaultProcessingService.class);

    private final SseLogService sseLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final AiAuditService aiAuditService;
    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    // Fixed: Merged into a single, comprehensive constructor for dependency injection
    public VaultProcessingService(ApplicationEventPublisher eventPublisher,
                                  AiAuditService aiAuditService,
                                  SseLogService sseLogService,
                                  AuditRecordRepository auditRecordRepository,
                                  ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.aiAuditService = aiAuditService;
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = objectMapper;
        this.sseLogService = sseLogService;
    }

    @Async
    public void processAndVault(String companyId, String documentType, String rawText) {
        logger.info("Starting background AI audit for Company: {} | Type: {}", companyId, documentType);

        // SSE LOG: Engine startup milestone
        sseLogService.log("INIT: HEURISTIC_ENGINE_LAUNCHED for Entity: " + companyId);

        try {
            // SSE LOG: Sending out text
            sseLogService.log("FORWARDING: Transmitting statement string matrices to Groq API socket...");
            String aiResponseRaw = aiAuditService.evaluateStatement(rawText, documentType);

            // SSE LOG: Text received back from Groq
            sseLogService.log("PARSING: Verification text payloads successfully returned from decision model.");

            String auditStatus = "ANOMALY_DETECTED"; // Conservative fallback
            try {
                JsonNode root = objectMapper.readTree(aiResponseRaw);
                if (root.has("status")) {
                    auditStatus = root.get("status").asText();
                }
            } catch (Exception parseEx) {
                logger.error("AI responded with invalid JSON structural format, defaulting status to anomaly.", parseEx);
                sseLogService.log("WARN: AI returned unstructured data format layout. Fallback defaults applied.");
            }

            AuditRecord record = new AuditRecord(companyId, documentType, rawText, aiResponseRaw, auditStatus);
            auditRecordRepository.save(record);
            eventPublisher.publishEvent(new AuditCompletedEvent(companyId, auditStatus, rawText));

            logger.info("Successfully vaulted financial audit report for Company: {}. Status: {}", companyId, auditStatus);

            // SSE LOG: Success state archived
            sseLogService.log("SECURE: Immutable forensic ledger records safely archived in active Vault. VERDICT: " + auditStatus);

        } catch (Exception ex) {
            logger.error("CRITICAL PIPELINE FAILURE for Company: " + companyId + " | Reason: " + ex.getMessage(), ex);
            // SSE LOG: Catching connection or processing errors
            sseLogService.log("CRITICAL ERROR: Pipeline execution exception trapped. Reason: " + ex.getMessage());
        }
    }

    @Async
    public void processAndVaultFile(String companyId, String documentType, MultipartFile file, String extractedText) {
        logger.info("Starting background multimodal document audit for Company: {} | File: {}", companyId, file.getOriginalFilename());

        // SSE LOG: Engine file processing startup milestone
        sseLogService.log("INIT: MULTIMODAL_ENGINE_LAUNCHED for File: " + file.getOriginalFilename());

        try {
            String aiResponseRaw = "";
            String filename = file.getOriginalFilename().toLowerCase();

            // SSE LOG: Signpost indicating OCR step completed and text is sending out
            sseLogService.log("PARSING: Text extraction completed. Transmitting string arrays to Groq connection pool...");

            if (filename.endsWith(".pdf") || filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                aiResponseRaw = aiAuditService.evaluateStatementDirect(
                        companyId,
                        documentType,
                        extractedText
                );
            } else {
                aiResponseRaw = aiAuditService.evaluateStatement(extractedText, documentType);
            }

            // SSE LOG: Text received back from Groq
            sseLogService.log("SUCCESS: Response frames securely generated from AI decision logic engine.");

            String auditStatus = "ANOMALY_DETECTED";
            try {
                JsonNode root = objectMapper.readTree(aiResponseRaw);
                if (root.has("status")) {
                    auditStatus = root.get("status").asText();
                }
            } catch (Exception parseEx) {
                logger.error("Failed to parse automated audit response JSON template schema.", parseEx);
                sseLogService.log("WARN: Failed to unpack response JSON schema format layers.");
            }

            String uiCompatibleReport = aiResponseRaw;
            if (aiResponseRaw != null && !aiResponseRaw.contains("\"choices\"")) {
                uiCompatibleReport = "{\n" +
                        "  \"choices\": [{\n" +
                        "    \"message\": {\n" +
                        "      \"content\": \"" + aiResponseRaw.replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"\n" +
                        "    }\n" +
                        "  }]\n" +
                        "}";
            }

            AuditRecord record = new AuditRecord(companyId, documentType, extractedText, uiCompatibleReport, auditStatus);
            auditRecordRepository.save(record);
            eventPublisher.publishEvent(new AuditCompletedEvent(companyId, auditStatus, extractedText));

            logger.info("Successfully completed multi-format document vaulting loop for company: {}", companyId);

            // SSE LOG: Finished everything safely
            sseLogService.log("SECURE: Immutable forensic ledger records safely archived in active Vault. VERDICT: " + auditStatus);

        } catch (Exception ex) {
            logger.error("CRITICAL FILE PIPELINE EXCEPTION for Company: " + companyId + " | " + ex.getMessage(), ex);
            // SSE LOG: Error catcher
            sseLogService.log("CRITICAL ERROR: Multimodal file pipeline processing failure: " + ex.getMessage());
        }
    }

    // Paste this inside your VaultProcessingService class body:
    public List<AuditRecord> getCompleteVaultLedger() {
        return auditRecordRepository.findAll();
    }
}
