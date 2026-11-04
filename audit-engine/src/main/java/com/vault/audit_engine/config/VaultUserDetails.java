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
    private final String fullName;

    public VaultUserDetails(String email, String passwordHash, Collection<? extends GrantedAuthority> authorities,
                            String tenantId, String fullName) {
        super(email, passwordHash, authorities);
        this.tenantId = tenantId;
        this.fullName = fullName;
    }

    public String getTenantId() { return tenantId; }
    public String getFullName() { return fullName; }
}
