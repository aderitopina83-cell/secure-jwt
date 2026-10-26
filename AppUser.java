package org.nwh.securejwt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** Hash Argon2id. A senha em texto nunca e guardada. */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int failedAttempts;

    private Instant lockedUntil;

    /**
     * Versao dos tokens deste utilizador (claim "ver"). Ao "terminar todas as
     * sessoes" incrementa e todos os access tokens anteriores deixam de valer.
     */
    @Column(nullable = false)
    private int tokenVersion;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {
        // JPA
    }

    public AppUser(String username, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.username = normalize(username);
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedAttempts = 0;
        this.createdAt = Instant.now();
    }

    public static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void registerFailedLogin(int maxAttempts, Duration lockout, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockout);
            failedAttempts = 0;
        }
    }

    public void resetFailedLogins() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /** Invalida todos os access tokens ja emitidos para este utilizador. */
    public void revokeAllTokens() {
        tokenVersion++;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
