package com.vault.audit_engine.model;

/**
 * @param figures the verified amounts from the audit report (unverified ones are null)
 */
public record AuditCompletedEvent(
        String companyId,
        String auditStatus,
        DocumentType documentType,
        StatementFigures figures
) {}
