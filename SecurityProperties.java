package org.nwh.securejwt.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Todas as definicoes de seguranca num so sitio (application.yml -> app.security.*).
 * A aplicacao nao arranca se algum valor obrigatorio faltar.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        /* Quem emite o token (claim "iss"). */
        @NotBlank String issuer,
        /* Para quem o token e valido (claim "aud"). */
        @NotBlank String audience,
        /* Vida curta do access token: 5-15 min. */
        @NotNull Duration accessTokenTtl,
        /* Vida do refresh token (ex.: um turno de trabalho). */
        @NotNull Duration refreshTokenTtl,
        /* Chave privada RSA (PKCS#8 PEM). Em producao: file:/run/secrets/jwt-private.pem */
        Resource privateKeyLocation,
        /* Chave publica RSA (X.509 PEM). */
        Resource publicKeyLocation,
        /* Identificador da chave (cabecalho "kid"), facilita rotacao. */
        @NotBlank String keyId,
        /* So em dev/testes: gera chaves temporarias se nao houver ficheiros. */
        boolean allowEphemeralKeys,
        /* Tentativas falhadas antes de bloquear a conta. */
        @Min(1) int maxFailedLogins,
        /* Tempo de bloqueio da conta. */
        @NotNull Duration lockoutDuration,
        /* Limite de pedidos de login por IP por minuto. */
        @Min(1) int maxLoginRequestsPerIpPerMinute
) {
}
