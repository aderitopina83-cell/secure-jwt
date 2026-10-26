package org.nwh.securejwt.web;

import jakarta.servlet.http.HttpServletRequest;
import org.nwh.securejwt.security.AuthCookies;
import org.nwh.securejwt.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Passos 5-8: paginas/recursos protegidos. */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final AuthService authService;

    public SessionController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return Map.of(
                "userId", jwt.getSubject(),
                "roles", roles == null ? List.of() : roles,
                "expiresAt", jwt.getExpiresAt());
    }

    @PostMapping("/session/logout-all")
    public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        authService.logoutEverywhere(UUID.fromString(jwt.getSubject()), http.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clearRefresh().toString())
                .build();
    }
}
