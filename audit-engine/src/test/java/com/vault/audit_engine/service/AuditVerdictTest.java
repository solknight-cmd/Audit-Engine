package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditReport;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ExtractedDocument.DocumentImage;
import com.vault.audit_engine.model.ModelAssessment;
import com.vault.audit_engine.model.ModelAssessment.Finding;
import com.vault.audit_engine.model.StatementFigures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.vault.audit_engine.service.AuditVerdict.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditVerdictTest {

    private static ExtractedDocument textDoc(String text) {
        return new ExtractedDocument("bs.pdf", "abc", text, List.of(), "PDF_TEXT", false);
    }

    private static ModelAssessment assessment(String assets, String liabilities, String equity,
                                              String status, Finding... findings) {
        StatementFigures figures = new StatementFigures(
                assets == null ? null : new BigDecimal(assets),
                liabilities == null ? null : new BigDecimal(liabilities),
                equity == null ? null : new BigDecimal(equity),
                "NGN", "31 Dec 2025", "thousands");
        return new ModelAssessment(figures, status, List.of(findings), "test-model");
    }

    private static final String STATEMENT = """
            Balance sheet as at 31 Dec 2025 (NGN thousands)      2025        2024
            Total assets                                    1,250,000.00   900,000
            Total liabilities                                 500,000.00   400,000
            Total equity                                      750,000.00   500,000
            Total liabilities and equity                    1,250,000.00   900,000
            """;

    @Test
    void figuresPrintedInTheDocumentArePassed() {
        AuditReport report = buildReport(textDoc(STATEMENT), assessment("1250000", "500000", "750000", "PASSED"));

        assertEquals(PASSED, report.status());
        assertEquals("CHECKED_AGAINST_TEXT", report.figureCheck());
        assertTrue(report.unverifiedFigures().isEmpty());
        assertEquals("test-model", report.model());
    }

    @Test
    void figureNotInTheDocumentIsUnverifiedAndNeverPasses() {
        // The model claims equity of 760,000, which is printed nowhere
        AuditReport report = buildReport(textDoc(STATEMENT), assessment("1250000", "500000", "760000", "PASSED"));

        assertEquals(UNABLE_TO_VERIFY, report.status());
        assertEquals(List.of("total_equity"), report.unverifiedFigures());
        assertNull(report.figures().totalEquity());
    }

    @Test
    void modelCannotOverrideFailedArithmetic() {
        String text = "Total assets 1,000\nTotal liabilities 400\nTotal equity 500";
        AuditReport report = buildReport(textDoc(text), assessment("1000", "400", "500", "PASSED"));

        assertEquals(ANOMALY_DETECTED, report.arithmeticStatus());
        assertEquals(ANOMALY_DETECTED, report.status());
    }

    @Test
    void highSeverityFindingMakesItAnAnomalyEvenWhenTheMathsBalances() {
        AuditReport report = buildReport(textDoc(STATEMENT), assessment("1250000", "500000", "750000", "PASSED",
                new Finding("HIGH", "Cash balance does not match the cash flow statement")));

        assertEquals(PASSED, report.arithmeticStatus());
        assertEquals(ANOMALY_DETECTED, report.status());
    }

    @Test
    void imageFiguresAreUsedButLabelledAsReadFromImages() {
        ExtractedDocument scanned = new ExtractedDocument("scan.pdf", "abc", "[Scanned PDF]",
                List.of(new DocumentImage("image/jpeg", "AAAA")), "PDF_SCANNED", false);
        AuditReport report = buildReport(scanned, assessment("100", "40", "60", "PASSED"));

        assertEquals(PASSED, report.status());
        assertEquals("READ_FROM_IMAGES", report.figureCheck());
    }

    @Test
    void arithmeticRules() {
        assertEquals(PASSED, arithmeticStatus(bd("100"), bd("40"), bd("60")));
        assertEquals(ANOMALY_DETECTED, arithmeticStatus(bd("100"), bd("40"), bd("50")));
        assertEquals(HIGH_LEVERAGE_RISK, arithmeticStatus(bd("100"), bd("90"), bd("10")));
        assertEquals(UNABLE_TO_VERIFY, arithmeticStatus(bd("100"), null, bd("60")));
    }

    @Test
    void numberMatchingHandlesSeparatorsDecimalsAndColumns() {
        String text = normalizeNumbers("Total assets 1,250,000.50 (900,000) 12,000");
        assertTrue(appearsInText(bd("1250000.5"), text));
        assertTrue(appearsInText(bd("-900000"), text)); // printed in parentheses
        assertFalse(appearsInText(bd("1250"), text));   // only part of a bigger number
        assertFalse(appearsInText(bd("900000900000"), normalizeNumbers("900,000 900,000"))); // year columns stay apart
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
