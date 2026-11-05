package com.vault.audit_engine.model;

/**
 * @param figures the verified figures from the audit report (unverified ones are null)
 */
public record AuditCompletedEvent(
        String companyId,
        String auditStatus,
        StatementFigures figures
) {}
