package com.toolize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Holds the admin password when it was generated automatically (no
 * TOOLIZE_ADMIN_PASSWORD configured) rather than chosen by the operator, so
 * a randomly generated password survives restarts and is shared by every
 * replica reading the same database - see SecurityConfig.
 */
@Entity
@Table(name = "admin_credential")
public class AdminCredentialEntity {

    @Id
    @Column(length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Instant generatedAt;

    protected AdminCredentialEntity() {
        // JPA
    }

    public AdminCredentialEntity(String username, String passwordHash, Instant generatedAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.generatedAt = generatedAt;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
