package org.nwh.securejwt.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Passo 6: de onde vem o token.
 *
 *  1. Cabecalho "Authorization: Bearer ..." (apps moveis, outros servidores)
 *  2. Senao, o cookie HttpOnly "__Host-access" (browser)
 *
 * Tokens em parametros de URL (?access_token=) sao recusados: ficam em logs
 * e no historico do browser.
 *
 * Em /api/auth/** nao se le token nenhum: assim um access token expirado nao
 * impede o pedido de refresh nem o logout.
 */
public class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    public CookieOrHeaderBearerTokenResolver() {
        headerResolver.setAllowUriQueryParameter(false);
        headerResolver.setAllowFormEncodedBodyParameter(false);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // So a API usa token. Paginas estaticas e /api/auth/** nao o leem.
        if (!path.startsWith("/api/") || path.startsWith(AuthCookies.REFRESH_PATH + "/")) {
            return null;
        }

        // Se o cliente mandou um cabecalho Authorization, so esse conta.
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return headerResolver.resolve(request);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthCookies.ACCESS.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
