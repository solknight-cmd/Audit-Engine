package com.vault.audit_engine.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A one-time link for accepting an invitation or resetting a password. Only the SHA-256 hash of
 * the link's secret is stored; the secret itself exists only in the link handed to the user.
 */
@Entity
@Table(name = "account_tokens")
public class AccountToken {

    public enum Purpose { INVITE, RESET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column
    private String createdBy; // Email of the admin who created the link, or "system"

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant usedAt;

    protected AccountToken() {}

    public AccountToken(String tokenHash, User user, Purpose purpose, String createdBy, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.purpose = purpose;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Purpose getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void markUsed(Instant when) { this.usedAt = when; }
}
