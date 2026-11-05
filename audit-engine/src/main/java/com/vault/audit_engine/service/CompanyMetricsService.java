package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditCompletedEvent;
import com.vault.audit_engine.model.CompanyMetrics;
import com.vault.audit_engine.model.DocumentType;
import com.vault.audit_engine.model.StatementFigures;
import com.vault.audit_engine.repository.AuditRecordRepository;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CompanyMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyMetricsService.class);

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

        // The headline cards show the latest balance sheet; other statements don't have these totals
        if (event.documentType() == DocumentType.BALANCE_SHEET) {
            StatementFigures figures = event.figures();
            metrics.setTotalAssets(formatAmount(figures.amount("total_assets"), figures));
            metrics.setTotalLiabilities(formatAmount(figures.amount("total_liabilities"), figures));
            metrics.setNetEquity(formatAmount(figures.amount("total_equity"), figures));
        }
        metrics.setLastAuditStatus(event.auditStatus());

        metricsRepository.save(metrics);
        logger.info("Metrics updated for {}: status {}", event.companyId(), event.auditStatus());
    }

    /** e.g. "NGN 1,250,000.00 thousands"; null when the figure is missing or unverified. */
    static String formatAmount(BigDecimal amount, StatementFigures figures) {
        if (amount == null) {
            return null;
        }
        StringBuilder formatted = new StringBuilder();
        if (figures.currency() != null) {
            formatted.append(figures.currency()).append(' ');
        }
        formatted.append(String.format("%,.2f", amount));
        if (figures.scale() != null && !figures.scale().equalsIgnoreCase("units")) {
            formatted.append(' ').append(figures.scale());
        }
        return formatted.toString();
    }
}
