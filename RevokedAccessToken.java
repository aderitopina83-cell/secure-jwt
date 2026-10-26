package org.nwh.securejwt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Lista de revogacao: jti de access tokens terminados por logout antes de expirar. */
@Entity
@Table(name = "revoked_access_token")
public class RevokedAccessToken {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    protected RevokedAccessToken() {
        // JPA
    }

    public RevokedAccessToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public String getJti() { return jti; }
    public Instant getExpiresAt() { return expiresAt; }
}
