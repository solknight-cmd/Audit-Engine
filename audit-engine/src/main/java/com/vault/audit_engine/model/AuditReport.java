package com.vault.audit_engine.model;

import java.util.List;

/**
 * The report stored in the vault for one audit (as JSON in {@code ai_audit_report}).
 *
 * @param status            final verdict: PASSED, HIGH_LEVERAGE_RISK, ANOMALY_DETECTED or UNABLE_TO_VERIFY
 * @param arithmeticStatus  the server's own check of Assets = Liabilities + Equity and leverage
 * @param modelStatus       the AI model's opinion, after counting its HIGH severity findings
 * @param figures           figures used for the arithmetic check; unverified ones are null
 * @param figureCheck       CHECKED_AGAINST_TEXT, or READ_FROM_IMAGES when there was no text to check against
 * @param unverifiedFigures figures the model reported that could not be found in the document text
 */
public record AuditReport(
        String status,
        String arithmeticStatus,
        String modelStatus,
        StatementFigures figures,
        String figureCheck,
        List<String> unverifiedFigures,
        List<ModelAssessment.Finding> findings,
        String model,
        String extractionMethod,
        boolean inputTruncated
) {}
