package com.vault.audit_engine.model;

public record AuditCompletedEvent(
        String companyId,
        String auditStatus,
        String rawText
) {}
