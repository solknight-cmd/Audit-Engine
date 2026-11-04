package com.vault.audit_engine.service;

/** The uploaded document can't be audited (wrong format, empty, encrypted, too large). Shown to the user as a 400. */
public class DocumentRejectedException extends RuntimeException {
    public DocumentRejectedException(String message) {
        super(message);
    }

    public DocumentRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
