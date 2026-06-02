package com.vault.audit_engine.controller;

import com.vault.audit_engine.model.CompanyMetrics;
import com.vault.audit_engine.repository.CompanyMetricsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    private final CompanyMetricsRepository metricsRepository;

    public MetricsController(CompanyMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<?> getCompanyMetrics(@PathVariable String companyId) {
        Optional<CompanyMetrics> metricsOpt = metricsRepository.findByCompanyId(companyId);

        // If it exists in the database, return it directly
        if (metricsOpt.isPresent()) {
            return ResponseEntity.ok(metricsOpt.get());
        }

        // Explicit Map return type structure to prevent ClassCastExceptions
        return ResponseEntity.ok(Map.of(
                "companyId", companyId,
                "totalAssets", "$0",
                "totalLiabilities", "$0",
                "netEquity", "$0",
                "totalAuditsProcessed", 0,
                "lastAuditStatus", "NONE"
        ));
    }
}
