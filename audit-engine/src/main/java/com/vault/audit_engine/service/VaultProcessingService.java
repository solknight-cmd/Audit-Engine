package com.vault.audit_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vault.audit_engine.model.AuditCompletedEvent;
import com.vault.audit_engine.model.AuditRecord;
import com.vault.audit_engine.model.AuditReport;
import com.vault.audit_engine.model.DocumentType;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ModelAssessment;
import com.vault.audit_engine.repository.AuditRecordRepository;
import com.vault.audit_engine.service.ai.AuditModelClient;
import com.vault.audit_engine.service.ai.AuditModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VaultProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(VaultProcessingService.class);

    private final SseLogService sseLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditModelClient modelClient;
    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    public VaultProcessingService(ApplicationEventPublisher eventPublisher,
                                  AuditModelClient modelClient,
                                  SseLogService sseLogService,
                                  AuditRecordRepository auditRecordRepository,
                                  ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.modelClient = modelClient;
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = objectMapper;
        this.sseLogService = sseLogService;
    }

    /**
     * Runs the AI review, computes the verdict and appends it to the vault. Only completed audits
     * are vaulted: if the AI provider fails, nothing is stored and the user is told to retry.
     */
    @Async
    public void process(String companyId, String documentType, ExtractedDocument document) {
        logger.info("Starting background audit for Company: {} | File: {} | Method: {}",
                companyId, document.sourceName(), document.method());
        sseLogService.log(companyId, "INIT: Audit started for " + document.sourceName() + " (" + document.method() + ")");

        try {
            sseLogService.log(companyId, document.hasImages()
                    ? "FORWARDING: Sending " + document.images().size() + " page image(s) to the vision model..."
                    : "FORWARDING: Sending extracted text to the AI model...");
            ModelAssessment assessment = modelClient.assess(documentType, document);

            DocumentType type = DocumentType.from(documentType);
            sseLogService.log(companyId, "VERIFYING: Checking the model's figures against the document and the "
                    + type.name().toLowerCase().replace('_', ' ') + " arithmetic...");
            AuditReport report = AuditVerdict.buildReport(document, type, assessment);
            if (!report.unverifiedFigures().isEmpty()) {
                sseLogService.log(companyId, "WARN: Figures not found in the document text: " + report.unverifiedFigures());
            }
            for (AuditReport.CheckResult check : report.checks()) {
                sseLogService.log(companyId, (check.passed() ? "CHECK PASSED: " : "WARN: CHECK FAILED: ") + check.name()
                        + (check.difference() == null ? "" : " (difference " + check.difference().toPlainString() + ")"));
            }

            AuditRecord record = new AuditRecord(companyId, documentType, document.text(),
                    objectMapper.writeValueAsString(report), report.status(),
                    document.sourceName(), document.sha256());
            auditRecordRepository.save(record);
            eventPublisher.publishEvent(new AuditCompletedEvent(companyId, report.status(), type, report.figures()));

            logger.info("Vaulted audit for Company: {}. Status: {}", companyId, report.status());
            sseLogService.log(companyId, "SECURE: Audit record appended to the vault. VERDICT: " + report.status());

        } catch (AuditModelException ex) {
            logger.warn("AI review failed for Company: {} | {}", companyId, ex.getMessage(), ex);
            sseLogService.log(companyId, "ERROR: " + ex.getMessage() + " Nothing was vaulted; please retry.");
        } catch (Exception ex) {
            logger.error("CRITICAL PIPELINE FAILURE for Company: {}", companyId, ex);
            sseLogService.log(companyId, "CRITICAL ERROR: The audit could not be completed. Nothing was vaulted.");
        }
    }

    public List<AuditRecord> getVaultLedger(String companyId) {
        return auditRecordRepository.findByCompanyIdOrderByVaultedAtDesc(companyId);
    }
}
