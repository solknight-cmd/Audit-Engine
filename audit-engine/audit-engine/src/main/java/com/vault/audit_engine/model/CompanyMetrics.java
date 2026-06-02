package com.vault.audit_engine.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_realtime_metrics")
public class CompanyMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private String companyId;

    @Column(name = "total_assets")
    private String totalAssets = "$0";

    @Column(name = "total_liabilities")
    private String totalLiabilities = "$0";

    @Column(name = "net_equity")
    private String netEquity = "$0";

    @Column(name = "total_audits_processed")
    private int totalAuditsProcessed = 0;

    @Column(name = "last_audit_status")
    private String lastAuditStatus;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated; // Changed to LocalDateTime

    public CompanyMetrics() {}

    public CompanyMetrics(String companyId) {
        this.companyId = companyId;
        this.lastUpdated = LocalDateTime.now(); // Updated
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public String getTotalAssets() { return totalAssets; }
    public void setTotalAssets(String totalAssets) { this.totalAssets = totalAssets; }
    public String getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(String totalLiabilities) { this.totalLiabilities = totalLiabilities; }
    public String getNetEquity() { return netEquity; }
    public void setNetEquity(String netEquity) { this.netEquity = netEquity; }
    public int getTotalAuditsProcessed() { return totalAuditsProcessed; }
    public void setTotalAuditsProcessed(int totalAuditsProcessed) { this.totalAuditsProcessed = totalAuditsProcessed; }
    public String getLastAuditStatus() { return lastAuditStatus; }
    public void setLastAuditStatus(String lastAuditStatus) { this.lastAuditStatus = lastAuditStatus; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
