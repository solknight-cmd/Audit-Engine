package com.vault.audit_engine.model;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "company_tenants")
public class CompanyTenant {

    @Id
    @Column(name = "tenant_id")
    private String tenantId; // e.g., "GLOBAL_CAPITAL_CORP_001", "VANGUARD_EQUITIES_LTD"

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "allowed_domain", unique = true)
    private String allowedDomain; // e.g. "globalcapital.com"; when set, only addresses at this domain can be invited

    @Column(name = "created_at")
    private ZonedDateTime createdAt = ZonedDateTime.now();

    // Constructors
    public CompanyTenant() {}

    public CompanyTenant(String tenantId, String companyName, String allowedDomain) {
        this.tenantId = tenantId;
        this.companyName = companyName;
        this.allowedDomain = normalizeDomain(allowedDomain);
    }

    // Getters and Setters
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getAllowedDomain() { return allowedDomain; }
    public void setAllowedDomain(String allowedDomain) { this.allowedDomain = normalizeDomain(allowedDomain); }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String trimmed = domain.trim().toLowerCase();
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }
}
