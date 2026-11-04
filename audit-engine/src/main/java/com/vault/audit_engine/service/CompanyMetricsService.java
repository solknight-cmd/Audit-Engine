package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditCompletedEvent;
import com.vault.audit_engine.model.CompanyMetrics;
import com.vault.audit_engine.repository.AuditRecordRepository;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompanyMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyMetricsService.class);

    // Accepts "Total Assets: $1,234.50", "Assets 100", "Assets = 100" etc.
    private static final String AMOUNT = "\\s*[:=]?\\s*\\$?\\s*([\\d,]+(?:\\.\\d+)?)";
    private static final Pattern ASSETS = Pattern.compile("\\b(?:Total\\s+)?Assets" + AMOUNT, Pattern.CASE_INSENSITIVE);
    private static final Pattern LIABILITIES = Pattern.compile("\\b(?:Total\\s+)?Liabilities" + AMOUNT, Pattern.CASE_INSENSITIVE);
    // Skip "Liabilities and Equity", which is the combined total, not equity itself
    private static final Pattern EQUITY = Pattern.compile(
            "(?<!and\\s)(?<!&\\s)\\b(?:Net\\s+|Total\\s+)?(?:Shareholders'?\\s+|Stockholders'?\\s+)?Equity" + AMOUNT,
            Pattern.CASE_INSENSITIVE);

    // Tolerance for rounding in the accounting equation check
    private static final BigDecimal TOLERANCE = BigDecimal.ONE;
    private static final BigDecimal HIGH_LEVERAGE_RATIO = new BigDecimal("0.80");

    private final CompanyMetricsRepository metricsRepository;
    private final AuditRecordRepository auditRecordRepository;

    public CompanyMetricsService(CompanyMetricsRepository metricsRepository,
                                 AuditRecordRepository auditRecordRepository) {
        this.metricsRepository = metricsRepository;
        this.auditRecordRepository = auditRecordRepository;
    }

    @Async
    @EventListener
    public void handleAuditCompleted(AuditCompletedEvent event) {
        CompanyMetrics metrics = metricsRepository.findByCompanyId(event.companyId())
                .orElseGet(() -> new CompanyMetrics(event.companyId()));

        // Derived from the vault itself, so it can't drift from the number of stored audits
        metrics.setTotalAuditsProcessed((int) auditRecordRepository.countByCompanyId(event.companyId()));
        metrics.setLastUpdated(LocalDateTime.now());

        BigDecimal assets = parseAmount(event.rawText(), ASSETS);
        BigDecimal liabilities = parseAmount(event.rawText(), LIABILITIES);
        BigDecimal equity = parseAmount(event.rawText(), EQUITY);

        metrics.setTotalAssets(formatAmount(assets));
        metrics.setTotalLiabilities(formatAmount(liabilities));
        metrics.setNetEquity(formatAmount(equity));
        metrics.setLastAuditStatus(determineAuditStatus(assets, liabilities, equity));

        metricsRepository.save(metrics);
        logger.info("Metrics updated for {}: status {}", event.companyId(), metrics.getLastAuditStatus());
    }

    /** Returns null when the figure can't be found, so callers can tell "missing" apart from zero. */
    private static BigDecimal parseAmount(String text, Pattern pattern) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal parseAssets(String text) { return parseAmount(text, ASSETS); }
    public static BigDecimal parseLiabilities(String text) { return parseAmount(text, LIABILITIES); }
    public static BigDecimal parseEquity(String text) { return parseAmount(text, EQUITY); }

    private static String formatAmount(BigDecimal amount) {
        return amount == null ? null : "$" + String.format("%,.2f", amount);
    }

    public static String determineAuditStatus(BigDecimal assets, BigDecimal liabilities, BigDecimal equity) {
        // Never report PASSED for figures we couldn't read
        if (assets == null || liabilities == null || equity == null) {
            return "UNABLE_TO_VERIFY";
        }

        // RULE 1: Accounting equation, Assets = Liabilities + Equity
        if (assets.subtract(liabilities.add(equity)).abs().compareTo(TOLERANCE) > 0) {
            return "ANOMALY_DETECTED";
        }

        // RULE 2: High leverage, Liabilities / Assets above 80%
        if (assets.signum() > 0
                && liabilities.compareTo(assets.multiply(HIGH_LEVERAGE_RATIO)) > 0) {
            return "HIGH_LEVERAGE_RISK";
        }

        return "PASSED";
    }
}
