package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditReport;
import com.vault.audit_engine.model.DocumentType;
import com.vault.audit_engine.model.ExtractedDocument;
import com.vault.audit_engine.model.ModelAssessment;
import com.vault.audit_engine.model.StatementFigures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns the AI model's assessment into the vault's verdict. The model is not trusted on its own:
 * its amounts must appear in the document text, the arithmetic for the document type is checked
 * here ({@link StatementChecks}), and the final status is the worse of those checks and the model's opinion.
 */
public final class AuditVerdict {

    public static final String PASSED = "PASSED";
    public static final String UNABLE_TO_VERIFY = "UNABLE_TO_VERIFY";
    public static final String HIGH_LEVERAGE_RISK = "HIGH_LEVERAGE_RISK";
    public static final String ANOMALY_DETECTED = "ANOMALY_DETECTED";

    // Ordered from best to worst; the final verdict is the worst of the checks
    private static final List<String> SEVERITY_ORDER = List.of(PASSED, UNABLE_TO_VERIFY, HIGH_LEVERAGE_RISK, ANOMALY_DETECTED);

    // Digit-group separators: "1,234,567" becomes "1234567". Plain spaces are left alone because
    // statements put year columns side by side ("1,000 900"), and merging those would corrupt both.
    private static final Pattern THOUSANDS_SEPARATOR = Pattern.compile("(?<=\\d)[,\\u00A0\\u202F](?=\\d{3}(?!\\d))");

    private AuditVerdict() {}

    public static AuditReport buildReport(ExtractedDocument document, DocumentType type, ModelAssessment assessment) {
        StatementFigures reported = assessment.figures();
        List<String> unverified = new ArrayList<>();
        String figureCheck;

        // Only the amounts this document type is checked on; anything else the model volunteered is dropped
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        if (document.hasImages()) {
            // No text layer to check against: amounts were read by the vision model
            type.amountFields().forEach(name -> amounts.put(name, reported.amount(name)));
            figureCheck = "READ_FROM_IMAGES";
        } else {
            String normalizedText = normalizeNumbers(document.text());
            type.amountFields().forEach(name ->
                    amounts.put(name, grounded(name, reported.amount(name), normalizedText, unverified)));
            figureCheck = "CHECKED_AGAINST_TEXT";
        }
        StatementFigures figures = reported.withAmounts(amounts);

        StatementChecks.Outcome checks = StatementChecks.run(type, figures);
        String modelStatus = modelStatus(assessment);

        return new AuditReport(
                worst(checks.status(), modelStatus),
                type,
                checks.status(),
                checks.checks(),
                modelStatus,
                figures,
                figureCheck,
                unverified,
                assessment.findings(),
                assessment.model(),
                document.method(),
                document.truncated());
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

    /** Keeps the amount only if that number is printed in the document; otherwise records it as unverified. */
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
