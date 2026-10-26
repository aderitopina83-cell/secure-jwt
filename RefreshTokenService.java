package org.nwh.securejwt.service;

import org.nwh.securejwt.config.SecurityProperties;
import org.nwh.securejwt.domain.AppUser;
import org.nwh.securejwt.domain.RefreshToken;
import org.nwh.securejwt.repository.AppUserRepository;
import org.nwh.securejwt.repository.RefreshTokenRepository;
import org.nwh.securejwt.security.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens com ROTACAO e DETECAO DE REUTILIZACAO.
 *
 * Cada uso devolve um token novo e inutiliza o anterior. Se um token ja usado
 * voltar a aparecer, alguem o copiou: revoga-se a familia inteira e todas as
 * sessoes do utilizador. O atacante e o utilizador legitimo perdem ambos o
 * acesso, e o utilizador volta a fazer login.
 */
@Service
public class RefreshTokenService {

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record Rotation(AppUser user, IssuedRefreshToken refreshToken) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;
    private final AppUserRepository users;
    private final SecurityProperties props;
    private final AuditLog audit;

    public RefreshTokenService(RefreshTokenRepository refreshTokens, AppUserRepository users,
                               SecurityProperties props, AuditLog audit) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.props = props;
        this.audit = audit;
    }

    @Transactional
    public IssuedRefreshToken create(AppUser user, UUID familyId) {
        byte[] bytes = new byte[32]; // 256 bits de aleatoriedade
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = Instant.now();
        RefreshToken token = new RefreshToken(sha256(raw), user.getId(), familyId, now, now.plus(props.refreshTokenTtl()));
        refreshTokens.save(token);
        return new IssuedRefreshToken(raw, token.getExpiresAt());
    }

    /** Vazio = token invalido (o chamador responde 401). */
    @Transactional
    public Optional<Rotation> rotate(String raw, String ip) {
        if (raw == null || raw.isBlank() || raw.length() > 100) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = refreshTokens.findForUpdate(sha256(raw));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();
        Instant now = Instant.now();

        if (current.isRevoked()) {
            // REUTILIZACAO DETETADA: possivel roubo de token
            refreshTokens.revokeFamily(current.getFamilyId());
            refreshTokens.revokeAllForUser(current.getUserId());
            users.findById(current.getUserId()).ifPresent(AppUser::revokeAllTokens);
            audit.warn("REFRESH_TOKEN_REUSE", current.getUserId().toString(), ip, "familia revogada");
            return Optional.empty();
        }
        if (current.isExpired(now)) {
            current.revoke();
            return Optional.empty();
        }

        Optional<AppUser> user = users.findById(current.getUserId());
        if (user.isEmpty() || !user.get().isEnabled()) {
            refreshTokens.revokeFamily(current.getFamilyId());
            return Optional.empty();
        }

        current.revoke();
        IssuedRefreshToken next = create(user.get(), current.getFamilyId());
        return Optional.of(new Rotation(user.get(), next));
    }

    @Transactional
    public void revokeFamilyOf(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 100) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(raw))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId()));
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
