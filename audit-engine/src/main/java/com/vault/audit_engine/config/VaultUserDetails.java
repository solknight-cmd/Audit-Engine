package com.vault.audit_engine.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * The logged-in user, carrying the tenant they belong to. Controllers take the tenant
 * from here, never from request parameters, so a user can only act on their own company.
 */
public class VaultUserDetails extends User {

    private final String tenantId;
    private final String companyName;
    private final String fullName;

    public VaultUserDetails(String email, String passwordHash, Collection<? extends GrantedAuthority> authorities,
                            String tenantId, String companyName, String fullName) {
        this(email, passwordHash, authorities, tenantId, companyName, fullName, false);
    }

    /** @param locked true while the account is locked after repeated failed logins; login is refused before the password is checked */
    public VaultUserDetails(String email, String passwordHash, Collection<? extends GrantedAuthority> authorities,
                            String tenantId, String companyName, String fullName, boolean locked) {
        super(email, passwordHash, true, true, true, !locked, authorities);
        this.tenantId = tenantId;
        this.companyName = companyName;
        this.fullName = fullName;
    }

    public String getTenantId() { return tenantId; }
    public String getCompanyName() { return companyName; }
    public String getFullName() { return fullName; }
}
