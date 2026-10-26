package org.nwh.securejwt.service;

import org.nwh.securejwt.config.SecurityProperties;
import org.nwh.securejwt.domain.AppUser;
import org.nwh.securejwt.repository.AppUserRepository;
import org.nwh.securejwt.repository.RefreshTokenRepository;
import org.nwh.securejwt.repository.RevokedAccessTokenRepository;
import org.nwh.securejwt.domain.RevokedAccessToken;
import org.nwh.securejwt.security.AuditLog;
import org.nwh.securejwt.security.LoginRateLimiter;
import org.nwh.securejwt.service.AuthExceptions.InvalidCredentialsException;
import org.nwh.securejwt.service.AuthExceptions.TooManyRequestsException;
import org.nwh.securejwt.service.RefreshTokenService.IssuedRefreshToken;
import org.nwh.securejwt.service.TokenService.IssuedAccessToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Passos 1-4: login.
 *
 *  - limite por IP + bloqueio por conta (forca bruta)
 *  - tempo de resposta igual exista ou nao o utilizador (enumeracao de contas)
 *  - mensagem de erro sempre igual
 *  - hash da senha atualizado automaticamente se os parametros do Argon2 subirem
 */
@Service
public class AuthService {

    public record LoginResult(AppUser user, IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {
    }

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokens;
    private final RevokedAccessTokenRepository revokedAccessTokens;
    private final LoginRateLimiter rateLimiter;
    private final SecurityProperties props;
    private final AuditLog audit;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
                       RefreshTokenService refreshTokenService, RefreshTokenRepository refreshTokens,
                       RevokedAccessTokenRepository revokedAccessTokens, LoginRateLimiter rateLimiter,
                       SecurityProperties props, AuditLog audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokens = refreshTokens;
        this.revokedAccessTokens = revokedAccessTokens;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.audit = audit;
        // Usado para gastar o mesmo tempo quando o utilizador nao existe
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // noRollbackFor: o contador de falhas tem de ficar gravado mesmo com erro
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResult login(String username, String password, String ip) {
        if (!rateLimiter.tryAcquire(ip)) {
            audit.warn("LOGIN_RATE_LIMITED", username, ip, null);
            throw new TooManyRequestsException();
        }

        Optional<AppUser> found = users.findByUsername(AppUser.normalize(username));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            audit.event("LOGIN_FAILED", username, ip, "utilizador desconhecido");
            throw new InvalidCredentialsException();
        }

        AppUser user = found.get();
        Instant now = Instant.now();

        if (user.isLocked(now)) {
            passwordEncoder.matches(password, dummyHash);
            audit.warn("LOGIN_BLOCKED", user.getUsername(), ip, "conta bloqueada ate " + user.getLockedUntil());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(props.maxFailedLogins(), props.lockoutDuration(), now);
            audit.event("LOGIN_FAILED", user.getUsername(), ip, "senha errada");
            if (user.isLocked(now)) {
                audit.warn("ACCOUNT_LOCKED", user.getUsername(), ip, "bloqueada ate " + user.getLockedUntil());
            }
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            audit.warn("LOGIN_DISABLED_ACCOUNT", user.getUsername(), ip, null);
            throw new InvalidCredentialsException();
        }

        user.resetFailedLogins();
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        IssuedAccessToken access = tokenService.issueAccessToken(user);
        IssuedRefreshToken refresh = refreshTokenService.create(user, UUID.randomUUID());
        audit.event("LOGIN_SUCCESS", user.getUsername(), ip, null);
        return new LoginResult(user, access, refresh);
    }

    /** Logout: revoga o access token atual (jti) e a familia de refresh tokens. */
    @Transactional
    public void logout(String accessJti, Instant accessExpiresAt, String rawRefreshToken, String ip) {
        if (accessJti != null && accessExpiresAt != null && !revokedAccessTokens.existsById(accessJti)) {
            revokedAccessTokens.save(new RevokedAccessToken(accessJti, accessExpiresAt));
        }
        refreshTokenService.revokeFamilyOf(rawRefreshToken);
        audit.event("LOGOUT", null, ip, accessJti == null ? "sem access token" : "jti=" + accessJti);
    }

    /** "Terminar sessao em todos os dispositivos". */
    @Transactional
    public void logoutEverywhere(UUID userId, String ip) {
        users.findById(userId).ifPresent(user -> {
            user.revokeAllTokens();
            refreshTokens.revokeAllForUser(userId);
            audit.event("LOGOUT_ALL", user.getUsername(), ip, null);
        });
    }
}
