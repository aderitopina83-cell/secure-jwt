package org.nwh.securejwt.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.nwh.securejwt.config.SecurityProperties;
import org.nwh.securejwt.security.AuthCookies;
import org.nwh.securejwt.service.AuthService;
import org.nwh.securejwt.service.AuthService.LoginResult;
import org.nwh.securejwt.service.RefreshTokenService;
import org.nwh.securejwt.service.RefreshTokenService.Rotation;
import org.nwh.securejwt.service.TokenService;
import org.nwh.securejwt.service.TokenService.IssuedAccessToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Limites de tamanho: uma senha de 1 MB faria o Argon2 trabalhar a toa (DoS). */
    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 256) String password) {
    }

    /** O token NAO vai no corpo: vai nos cookies HttpOnly. */
    public record SessionResponse(Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt) {
    }

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final JwtDecoder jwtDecoder;
    private final SecurityProperties props;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
                          TokenService tokenService, JwtDecoder jwtDecoder, SecurityProperties props) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
        this.jwtDecoder = jwtDecoder;
        this.props = props;
    }

    /** O frontend chama isto primeiro para receber o cookie XSRF-TOKEN. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    /** Passos 1-4. */
    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResult result = authService.login(request.username(), request.password(), http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.access(result.accessToken().value(), props.accessTokenTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookies.refresh(result.refreshToken().value(), props.refreshTokenTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SessionResponse(result.accessToken().expiresAt(), result.refreshToken().expiresAt()));
    }

    /** Renovar o access token sem pedir a senha outra vez. O refresh token roda a cada uso. */
    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
            HttpServletRequest http) {

        Optional<Rotation> rotation = refreshTokenService.rotate(refreshToken, http.getRemoteAddr());
        if (rotation.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, AuthCookies.clearAccess().toString())
                    .header(HttpHeaders.SET_COOKIE, AuthCookies.clearRefresh().toString())
                    .build();
        }

        Rotation r = rotation.get();
        IssuedAccessToken access = tokenService.issueAccessToken(r.user());
        Duration refreshMaxAge = Duration.between(Instant.now(), r.refreshToken().expiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.access(access.value(), props.accessTokenTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookies.refresh(r.refreshToken().value(), refreshMaxAge).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SessionResponse(access.expiresAt(), r.refreshToken().expiresAt()));
    }

    /** Logout real: o token fica revogado no servidor, nao basta apagar o cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.ACCESS, required = false) String accessCookie,
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken,
            HttpServletRequest http) {

        String rawAccess = accessCookie;
        String authHeader = http.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            rawAccess = authHeader.substring(7).trim();
        }

        String jti = null;
        Instant expiresAt = null;
        if (rawAccess != null && !rawAccess.isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(rawAccess);
                jti = jwt.getId();
                expiresAt = jwt.getExpiresAt();
            } catch (JwtException ignored) {
                // expirado, invalido ou ja revogado: nada a revogar
            }
        }

        authService.logout(jti, expiresAt, refreshToken, http.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clearRefresh().toString())
                .build();
    }
}
