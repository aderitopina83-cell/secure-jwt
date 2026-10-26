package org.nwh.securejwt.service;

import org.nwh.securejwt.repository.RefreshTokenRepository;
import org.nwh.securejwt.repository.RevokedAccessTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Limpa tokens ja expirados (deixam de ter utilidade na lista de revogacao). */
@Component
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokens;
    private final RevokedAccessTokenRepository revokedAccessTokens;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, RevokedAccessTokenRepository revokedAccessTokens) {
        this.refreshTokens = refreshTokens;
        this.revokedAccessTokens = revokedAccessTokens;
    }

    @Scheduled(cron = "0 15 * * * *") // de hora a hora
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now();
        revokedAccessTokens.deleteExpired(cutoff);
        refreshTokens.deleteExpired(cutoff);
    }
}
