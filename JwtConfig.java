package org.nwh.securejwt.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.nwh.securejwt.security.TokenRevocationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

/**
 * Passo 3 (criar e assinar) e passo 7 (verificar assinatura + claims) do diagrama.
 *
 * - RS256: a chave PRIVADA assina, a PUBLICA verifica. Quem so verifica nunca
 *   consegue forjar tokens.
 * - O decoder aceita APENAS RS256: "alg: none" e a troca RS256 -> HS256
 *   (algorithm confusion) sao rejeitados.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_RSA_BITS = 2048;

    public record RsaKeys(String keyId, RSAPublicKey publicKey, RSAPrivateKey privateKey) {
    }

    @Bean
    RsaKeys rsaKeys(SecurityProperties props) throws Exception {
        if (props.privateKeyLocation() != null && props.publicKeyLocation() != null) {
            RSAPrivateKey privateKey;
            RSAPublicKey publicKey;
            try (InputStream in = props.privateKeyLocation().getInputStream()) {
                privateKey = RsaKeyConverters.pkcs8().convert(in);
            }
            try (InputStream in = props.publicKeyLocation().getInputStream()) {
                publicKey = RsaKeyConverters.x509().convert(in);
            }
            if (publicKey.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalStateException("Chave RSA demasiado curta: minimo " + MIN_RSA_BITS + " bits");
            }
            log.info("Chaves JWT carregadas de ficheiro (kid={})", props.keyId());
            return new RsaKeys(props.keyId(), publicKey, privateKey);
        }

        if (!props.allowEphemeralKeys()) {
            throw new IllegalStateException(
                    "Faltam app.security.private-key-location / public-key-location. "
                            + "Chaves temporarias so sao permitidas em dev (allow-ephemeral-keys=true).");
        }

        log.warn("A gerar chaves RSA TEMPORARIAS. Os tokens deixam de valer ao reiniciar. NUNCA usar em producao.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        return new RsaKeys(props.keyId(), (RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
    }

    /** Passo 3: assinar. */
    @Bean
    JwtEncoder jwtEncoder(RsaKeys keys) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(keys.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    /** Passo 7: verificar assinatura e TODAS as claims em cada pedido. */
    @Bean
    JwtDecoder jwtDecoder(RsaKeys keys, SecurityProperties props, TokenRevocationValidator revocationValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256) // algoritmo fixo
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(Duration.ofSeconds(30)), // exp / nbf com tolerancia curta
                new JwtIssuerValidator(props.issuer()),            // iss
                audienceValidator(props.audience()),               // aud
                requiredClaimsValidator(),                         // sub, jti, iat, exp obrigatorios
                revocationValidator                                // logout / conta desativada
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience invalida", null));
        };
    }

    private static OAuth2TokenValidator<Jwt> requiredClaimsValidator() {
        return jwt -> {
            boolean ok = jwt.getSubject() != null
                    && jwt.getId() != null
                    && jwt.getIssuedAt() != null
                    && jwt.getExpiresAt() != null;
            return ok
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Claims obrigatorias em falta", null));
        };
    }
}
