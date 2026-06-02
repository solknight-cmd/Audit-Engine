package com.vault.audit_engine.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "financial_audit_vault")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String companyId;

    @Column(nullable = false, updatable = false)
    private String documentType; // e.g., BALANCE_SHEET, INCOME_STATEMENT, ALL

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String rawExtractedText; // The parsed financial statement text

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String aiAuditReport; // The JSON analysis returned by the AI

    @Column(nullable = false, updatable = false)
    private String auditStatus; // e.g., PASSED, ANOMALY_DETECTED

    @Column(nullable = false, updatable = false)
    private Instant vaultedAt;

    // Required by Hibernate
    protected AuditRecord() {}

    public AuditRecord(String companyId, String documentType, String rawExtractedText, String aiAuditReport, String auditStatus) {
        this.companyId = companyId;
        this.documentType = documentType;
        this.rawExtractedText = rawExtractedText;
        this.aiAuditReport = aiAuditReport;
        this.auditStatus = auditStatus;
        this.vaultedAt = Instant.now();
    }

    // Explicitly exposing GETTERS only. No setters allowed to prevent manipulation.
    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getDocumentType() { return documentType; }
    public String getRawExtractedText() { return rawExtractedText; }
    public String getAiAuditReport() { return aiAuditReport; }
    public String getAuditStatus() { return auditStatus; }
    public Instant getVaultedAt() { return vaultedAt; }
}
