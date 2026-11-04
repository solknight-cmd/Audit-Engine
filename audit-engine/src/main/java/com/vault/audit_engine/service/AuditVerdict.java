package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditReport;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ModelAssessment;
import com.vault.audit_engine.model.StatementFigures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns the AI model's assessment into the vault's verdict. The model is not trusted on its own:
 * its figures must appear in the document text, the accounting equation is checked here, and the
 * final status is the worse of the arithmetic check and the model's opinion.
 */
public final class AuditVerdict {

    public static final String PASSED = "PASSED";
    public static final String UNABLE_TO_VERIFY = "UNABLE_TO_VERIFY";
    public static final String HIGH_LEVERAGE_RISK = "HIGH_LEVERAGE_RISK";
    public static final String ANOMALY_DETECTED = "ANOMALY_DETECTED";

    // Ordered from best to worst; the final verdict is the worst of the checks
    private static final List<String> SEVERITY_ORDER = List.of(PASSED, UNABLE_TO_VERIFY, HIGH_LEVERAGE_RISK, ANOMALY_DETECTED);

    // Tolerance for rounding in the accounting equation check
    private static final BigDecimal TOLERANCE = BigDecimal.ONE;
    private static final BigDecimal HIGH_LEVERAGE_RATIO = new BigDecimal("0.80");

    // Digit-group separators: "1,234,567" becomes "1234567". Plain spaces are left alone because
    // statements put year columns side by side ("1,000 900"), and merging those would corrupt both.
    private static final Pattern THOUSANDS_SEPARATOR = Pattern.compile("(?<=\\d)[,\\u00A0\\u202F](?=\\d{3}(?!\\d))");

    private AuditVerdict() {}

    public static AuditReport buildReport(ExtractedDocument document, ModelAssessment assessment) {
        StatementFigures reported = assessment.figures();
        List<String> unverified = new ArrayList<>();
        StatementFigures figures;
        String figureCheck;

        if (document.hasImages()) {
            // No text layer to check against: figures were read by the vision model
            figures = reported;
            figureCheck = "READ_FROM_IMAGES";
        } else {
            String normalizedText = normalizeNumbers(document.text());
            figures = new StatementFigures(
                    grounded("total_assets", reported.totalAssets(), normalizedText, unverified),
                    grounded("total_liabilities", reported.totalLiabilities(), normalizedText, unverified),
                    grounded("total_equity", reported.totalEquity(), normalizedText, unverified),
                    reported.currency(), reported.period(), reported.scale());
            figureCheck = "CHECKED_AGAINST_TEXT";
        }

        String arithmeticStatus = arithmeticStatus(figures.totalAssets(), figures.totalLiabilities(), figures.totalEquity());
        String modelStatus = modelStatus(assessment);

        return new AuditReport(
                worst(arithmeticStatus, modelStatus),
                arithmeticStatus,
                modelStatus,
                figures,
                figureCheck,
                unverified,
                assessment.findings(),
                assessment.model(),
                document.method(),
                document.truncated());
    }

    public static String arithmeticStatus(BigDecimal assets, BigDecimal liabilities, BigDecimal equity) {
        // Never report PASSED for figures we couldn't read or verify
        if (assets == null || liabilities == null || equity == null) {
            return UNABLE_TO_VERIFY;
        }

        // RULE 1: Accounting equation, Assets = Liabilities + Equity
        if (assets.subtract(liabilities.add(equity)).abs().compareTo(TOLERANCE) > 0) {
            return ANOMALY_DETECTED;
        }

        // RULE 2: High leverage, Liabilities / Assets above 80%
        if (assets.signum() > 0 && liabilities.compareTo(assets.multiply(HIGH_LEVERAGE_RATIO)) > 0) {
            return HIGH_LEVERAGE_RISK;
        }

        return PASSED;
    }

    /** The model's verdict, treated as an anomaly if it says anything other than PASSED or raised a HIGH finding. */
    static String modelStatus(ModelAssessment assessment) {
        boolean highFinding = assessment.findings().stream()
                .anyMatch(f -> f.severity() != null && f.severity().equalsIgnoreCase("HIGH"));
        return PASSED.equalsIgnoreCase(assessment.status()) && !highFinding ? PASSED : ANOMALY_DETECTED;
    }

    static String worst(String first, String second) {
        return SEVERITY_ORDER.indexOf(first) >= SEVERITY_ORDER.indexOf(second) ? first : second;
    }

    /** Keeps the figure only if that number is printed in the document; otherwise records it as unverified. */
    private static BigDecimal grounded(String name, BigDecimal value, String normalizedText, List<String> unverified) {
        if (value == null) {
            return null;
        }
        if (appearsInText(value, normalizedText)) {
            return value;
        }
        unverified.add(name);
        return null;
    }

    static boolean appearsInText(BigDecimal value, String normalizedText) {
        // Sign is ignored: negatives are printed as "(1,000)" or "-1,000"
        String digits = value.abs().stripTrailingZeros().toPlainString();
        String trailing = digits.contains(".") ? "0*" : "(?:\\.0+)?";
        Pattern pattern = Pattern.compile("(?<![\\d.])" + Pattern.quote(digits) + trailing + "(?![\\d])");
        return pattern.matcher(normalizedText).find();
    }

    static String normalizeNumbers(String text) {
        return text == null ? "" : THOUSANDS_SEPARATOR.matcher(text).replaceAll("");
    }
}
