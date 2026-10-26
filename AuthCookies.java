package org.nwh.securejwt.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Passo 4: o token volta num cookie que o JavaScript NAO consegue ler.
 *
 *  HttpOnly        -> um ataque XSS nao rouba o token
 *  Secure          -> so viaja por HTTPS
 *  SameSite=Strict -> o browser nao o envia em pedidos vindos de outros sites (CSRF)
 *  __Host-         -> o browser exige Secure + Path=/ + sem Domain (nao pode ser
 *                     sobrescrito por um subdominio)
 *
 * O refresh token so e enviado para /api/auth, nunca para o resto da API.
 */
public final class AuthCookies {

    public static final String ACCESS = "__Host-access";
    public static final String REFRESH = "__Secure-refresh";
    public static final String REFRESH_PATH = "/api/auth";

    private AuthCookies() {
    }

    public static ResponseCookie access(String token, Duration maxAge) {
        return base(ACCESS, token, "/").maxAge(maxAge).build();
    }

    public static ResponseCookie refresh(String token, Duration maxAge) {
        return base(REFRESH, token, REFRESH_PATH).maxAge(maxAge).build();
    }

    public static ResponseCookie clearAccess() {
        return base(ACCESS, "", "/").maxAge(0).build();
    }

    public static ResponseCookie clearRefresh() {
        return base(REFRESH, "", REFRESH_PATH).maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path);
    }
}
