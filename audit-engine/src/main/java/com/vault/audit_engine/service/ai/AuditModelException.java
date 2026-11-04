package com.vault.audit_engine.service.ai;

/** The AI provider failed or returned something unusable. No verdict is vaulted when this happens. */
public class AuditModelException extends RuntimeException {
    public AuditModelException(String message) {
        super(message);
    }

    public AuditModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
