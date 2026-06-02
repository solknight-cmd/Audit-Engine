package com.vault.audit_engine.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vault_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email; // e.g., "alex@globalcapital.com"

    @Column(nullable = false)
    private String password; // Will hold secure BCrypt hashes!

    @Column(nullable = false)
    private String fullName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private CompanyTenant tenant;

    @Column(nullable = false)
    private String role; // e.g., "ROLE_AUDITOR", "ROLE_ADMIN"

    // Constructors
    public User() {}

    // Getters and Setters
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public CompanyTenant getTenant() { return tenant; }
    public void setTenant(CompanyTenant tenant) { this.tenant = tenant; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
