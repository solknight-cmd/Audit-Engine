package com.vault.audit_engine.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The report stored in the vault for one audit (as JSON in {@code ai_audit_report}).
 *
 * @param status            final verdict: PASSED, HIGH_LEVERAGE_RISK, ANOMALY_DETECTED or UNABLE_TO_VERIFY
 * @param documentType      the kind of statement the checks were chosen for
 * @param arithmeticStatus  result of the server's own checks for this document type
 * @param checks            every check the server ran, so a reviewer can see what passed and by how much
 * @param modelStatus       the AI model's opinion, after counting its HIGH severity findings
 * @param figures           amounts used for the checks; unverified ones are null
 * @param figureCheck       CHECKED_AGAINST_TEXT, or READ_FROM_IMAGES when there was no text to check against
 * @param unverifiedFigures amounts the model reported that could not be found in the document text
 */
public record AuditReport(
        String status,
        DocumentType documentType,
        String arithmeticStatus,
        List<CheckResult> checks,
        String modelStatus,
        StatementFigures figures,
        String figureCheck,
        List<String> unverifiedFigures,
        List<ModelAssessment.Finding> findings,
        String model,
        String extractionMethod,
        boolean inputTruncated
) {
    /**
     * One check, e.g. "Assets = Liabilities + Equity".
     *
     * @param difference left side minus right side, in the document's units; null for checks that aren't equations
     */
    public record CheckResult(String name, boolean passed, BigDecimal difference) {}
}
