package com.vault.audit_engine.service;

import com.vault.audit_engine.model.AuditCompletedEvent;
import com.vault.audit_engine.model.CompanyMetrics;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompanyMetricsService {

    private final CompanyMetricsRepository metricsRepository;
    private final List<SseEmitter> emitters = new ArrayList<>();

    public CompanyMetricsService(CompanyMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    // Create a streaming subscription channel for UI dashboards
    public SseEmitter subscribeToLiveMetrics() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Keep open
        this.emitters.add(emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((ex) -> this.emitters.remove(emitter));

        return emitter;
    }

    private String parseValue(String text, String regex) {
        // Catch blank document extractions early before compiling patterns!
        if (text == null || text.strip().isEmpty()) {
            return "$0";
        }

        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "$0";
    }

    private void broadcastUpdate(CompanyMetrics updatedMetrics) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("metrics-update")
                        .data(updatedMetrics));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    @Async
    @EventListener
    public void handleAuditCompleted(AuditCompletedEvent event) {
        CompanyMetrics metrics = metricsRepository.findByCompanyId(event.companyId())
                .orElseGet(() -> new CompanyMetrics(event.companyId()));

        metrics.setTotalAuditsProcessed(metrics.getTotalAuditsProcessed() + 1);

        // 1. Keep your updated timestamp logic here
        metrics.setLastUpdated(LocalDateTime.now()); // Or your fixed time object

        // 2. PLACE THE NEW EXTRACTION AND ALGORITHMIC STATUS LOGIC HERE:
        String assets = parseValue(event.rawText(), "(?:Total\\s+)?Assets\\s+(\\d+)");
        String liabilities = parseValue(event.rawText(), "(?:Total\\s+)?Liabilities\\s+(\\d+)");
        String equity = parseValue(event.rawText(), "(?:Net\\s+)?Equity\\s+(\\d+)");

        metrics.setTotalAssets(assets);
        metrics.setTotalLiabilities(liabilities);
        metrics.setNetEquity(equity);

        // Calculate the actual algorithmic health status instead of accepting a generic event status!
        String calculatedStatus = determineAuditStatus(assets, liabilities, equity);
        metrics.setLastAuditStatus(calculatedStatus);

        // 3. Save into your isolated metrics table (Line 58 in your screenshot)
        CompanyMetrics updatedMetrics = metricsRepository.save(metrics);

        // Broadcast out to all active UI dash connections instantly
        broadcastUpdate(updatedMetrics);
    }

    public String determineAuditStatus(String assetsStr, String liabilitiesStr, String equityStr) {
        try {
            // Fallback checks if any extraction yields a null or empty block
            if (assetsStr == null || liabilitiesStr == null || equityStr == null) {
                return "UNABLE_TO_VERIFY";
            }

            // Strip everything except numbers and decimal points, then parse safely as doubles
            double assets = Double.parseDouble(assetsStr.replaceAll("[^0-9.]", ""));
            double liabilities = Double.parseDouble(liabilitiesStr.replaceAll("[^0-9.]", ""));
            double equity = Double.parseDouble(equityStr.replaceAll("[^0-9.]", ""));

            // RULE 1: Accounting Equation Validation (using a small margin for decimal rounding)
            if (Math.abs(assets - (liabilities + equity)) > 1.0) {
                return "ANOMALY_DETECTED";
            }

            // RULE 2: High Leverage Risk Check
            if (assets > 0 && (liabilities / assets) > 0.80) {
                return "HIGH_LEVERAGE_RISK";
            }

            return "PASSED";

        } catch (Exception e) {
            System.err.println("Metrics extraction calculation crash avoided: " + e.getMessage());
            return "UNABLE_TO_VERIFY";
        }
    }
}
