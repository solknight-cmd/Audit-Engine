package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditReport;
import com.vault.audit_engine.model.DocumentType;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ExtractedDocument.DocumentImage;
import com.vault.audit_engine.model.ModelAssessment;
import com.vault.audit_engine.model.ModelAssessment.Finding;
import com.vault.audit_engine.model.StatementFigures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vault.audit_engine.service.AuditVerdict.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditVerdictTest {

    private static ExtractedDocument textDoc(String text) {
        return new ExtractedDocument("bs.pdf", "abc", text, List.of(), "PDF_TEXT", false);
    }

    private static ModelAssessment balanceSheet(String assets, String liabilities, String equity,
                                                String status, Finding... findings) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        amounts.put("total_assets", assets == null ? null : new BigDecimal(assets));
        amounts.put("total_liabilities", liabilities == null ? null : new BigDecimal(liabilities));
        amounts.put("total_equity", equity == null ? null : new BigDecimal(equity));
        return new ModelAssessment(new StatementFigures(amounts, "NGN", "31 Dec 2025", "thousands"),
                status, List.of(findings), "test-model");
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
        AuditReport report = buildReport(textDoc(STATEMENT), DocumentType.BALANCE_SHEET,
                balanceSheet("1250000", "500000", "750000", "PASSED"));

        assertEquals(PASSED, report.status());
        assertEquals(DocumentType.BALANCE_SHEET, report.documentType());
        assertEquals("CHECKED_AGAINST_TEXT", report.figureCheck());
        assertTrue(report.unverifiedFigures().isEmpty());
        assertEquals("Assets = Liabilities + Equity", report.checks().get(0).name());
        assertEquals("test-model", report.model());
    }

    @Test
    void figureNotInTheDocumentIsUnverifiedAndNeverPasses() {
        // The model claims equity of 760,000, which is printed nowhere
        AuditReport report = buildReport(textDoc(STATEMENT), DocumentType.BALANCE_SHEET,
                balanceSheet("1250000", "500000", "760000", "PASSED"));

        assertEquals(UNABLE_TO_VERIFY, report.status());
        assertEquals(List.of("total_equity"), report.unverifiedFigures());
        assertNull(report.figures().amount("total_equity"));
        // The leverage ratio can still be shown, but it isn't enough to pass
        assertTrue(report.checks().stream().allMatch(c -> c.difference() == null));
    }

    @Test
    void modelCannotOverrideFailedArithmetic() {
        String text = "Total assets 1,000\nTotal liabilities 400\nTotal equity 500";
        AuditReport report = buildReport(textDoc(text), DocumentType.BALANCE_SHEET,
                balanceSheet("1000", "400", "500", "PASSED"));

        assertEquals(ANOMALY_DETECTED, report.arithmeticStatus());
        assertEquals(ANOMALY_DETECTED, report.status());
        assertEquals(new BigDecimal("100"), report.checks().get(0).difference());
    }

    @Test
    void highSeverityFindingMakesItAnAnomalyEvenWhenTheMathsBalances() {
        AuditReport report = buildReport(textDoc(STATEMENT), DocumentType.BALANCE_SHEET,
                balanceSheet("1250000", "500000", "750000", "PASSED",
                        new Finding("HIGH", "Cash balance does not match the cash flow statement")));

        assertEquals(PASSED, report.arithmeticStatus());
        assertEquals(ANOMALY_DETECTED, report.status());
    }

    @Test
    void imageFiguresAreUsedButLabelledAsReadFromImages() {
        ExtractedDocument scanned = new ExtractedDocument("scan.pdf", "abc", "[Scanned PDF]",
                List.of(new DocumentImage("image/jpeg", "AAAA")), "PDF_SCANNED", false);
        AuditReport report = buildReport(scanned, DocumentType.BALANCE_SHEET, balanceSheet("100", "40", "60", "PASSED"));

        assertEquals(PASSED, report.status());
        assertEquals("READ_FROM_IMAGES", report.figureCheck());
    }

    @Test
    void amountsOutsideTheDocumentTypeAreDropped() {
        // The model answered with balance sheet totals for a trial balance upload
        AuditReport report = buildReport(textDoc(STATEMENT), DocumentType.TRIAL_BALANCE,
                balanceSheet("1250000", "500000", "750000", "PASSED"));

        assertEquals(List.of("total_debits", "total_credits"), List.copyOf(report.figures().amounts().keySet()));
        assertEquals(UNABLE_TO_VERIFY, report.status());
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
