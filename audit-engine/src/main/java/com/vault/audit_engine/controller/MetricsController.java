package com.vault.audit_engine.controller;

import com.vault.audit_engine.config.VaultUserDetails;
import com.vault.audit_engine.model.CompanyMetrics;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final CompanyMetricsRepository metricsRepository;

    public MetricsController(CompanyMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    // Metrics for the caller's own company only
    @GetMapping
    public ResponseEntity<?> getCompanyMetrics(@AuthenticationPrincipal VaultUserDetails user) {
        String companyId = user.getTenantId();
        Optional<CompanyMetrics> metricsOpt = metricsRepository.findByCompanyId(companyId);

        // If it exists in the database, return it directly
        if (metricsOpt.isPresent()) {
            return ResponseEntity.ok(metricsOpt.get());
        }

        // No audits yet. HashMap because Map.of rejects the null figures.
        Map<String, Object> empty = new HashMap<>();
        empty.put("companyId", companyId);
        empty.put("totalAssets", null);
        empty.put("totalLiabilities", null);
        empty.put("netEquity", null);
        empty.put("totalAuditsProcessed", 0);
        empty.put("lastAuditStatus", "NONE");
        return ResponseEntity.ok(empty);
    }
}
