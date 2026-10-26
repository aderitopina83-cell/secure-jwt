package org.nwh.securejwt.security;

import org.nwh.securejwt.domain.AppUser;
import org.nwh.securejwt.repository.AppUserRepository;
import org.nwh.securejwt.repository.RevokedAccessTokenRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Uma assinatura valida nao chega. Em cada pedido tambem se confirma que:
 *  - o token nao foi revogado por logout (jti na lista de revogacao);
 *  - o utilizador ainda existe e esta ativo;
 *  - a versao do token (claim "ver") e a atual: "terminar todas as sessoes"
 *    ou deteccao de roubo incrementam a versao e matam os tokens antigos.
 */
@Component
public class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error("invalid_token", "Token revogado ou utilizador inativo", null);

    private final RevokedAccessTokenRepository revokedTokens;
    private final AppUserRepository users;

    public TokenRevocationValidator(RevokedAccessTokenRepository revokedTokens, AppUserRepository users) {
        this.revokedTokens = revokedTokens;
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null || revokedTokens.existsById(jti)) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        Optional<AppUser> user = users.findById(userId);
        if (user.isEmpty() || !user.get().isEnabled()) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        Object version = jwt.getClaim("ver");
        if (!(version instanceof Number n) || n.intValue() != user.get().getTokenVersion()) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
