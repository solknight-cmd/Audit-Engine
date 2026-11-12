package com.vault.audit_engine.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "vault_users")
public class User {

    public static final String ROLE_AUDITOR = "ROLE_AUDITOR";
    /** Manages the users of their own company. */
    public static final String ROLE_COMPANY_ADMIN = "ROLE_COMPANY_ADMIN";
    /** The Vault Core operator: creates companies and can manage any account. */
    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    public enum Status { INVITED, ACTIVE, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email; // Stored in lower case, e.g. "alex@globalcapital.com"

    @Column
    private String password; // BCrypt hash; null until an invited user sets one

    @Column(nullable = false)
    private String fullName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private CompanyTenant tenant;

    @Column(nullable = false)
    private String role; // One of the ROLE_ constants above

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant lastLoginAt;

    // Constructors
    public User() {}

    // Getters and Setters
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public CompanyTenant getTenant() { return tenant; }
    public void setTenant(CompanyTenant tenant) { this.tenant = tenant; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
