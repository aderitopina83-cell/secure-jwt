package org.nwh.securejwt.service;

import org.nwh.securejwt.config.JwtConfig.RsaKeys;
import org.nwh.securejwt.config.SecurityProperties;
import org.nwh.securejwt.domain.AppUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Passo 3: criar e assinar o access token.
 *
 * O conteudo de um JWT e LEGIVEL por qualquer pessoa (so esta assinado, nao
 * encriptado). Por isso aqui so entram identificadores e papeis, nunca dados
 * pessoais ou clinicos.
 */
@Service
public class TokenService {

    public record IssuedAccessToken(String value, String jti, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final RsaKeys keys;

    public TokenService(JwtEncoder encoder, SecurityProperties props, RsaKeys keys) {
        this.encoder = encoder;
        this.props = props;
        this.keys = keys;
    }

    public IssuedAccessToken issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.accessTokenTtl());
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .audience(List.of(props.audience()))
                .subject(user.getId().toString())   // UUID, nao o email
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .id(jti)
                .claim("roles", List.of(user.getRole().name()))
                .claim("ver", user.getTokenVersion())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keys.keyId())
                .type("JWT")
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, jti, expiresAt);
    }
}
