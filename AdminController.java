package org.nwh.securejwt.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Autorizacao, nao so autenticacao: um token valido nao chega, o papel tambem
 * tem de ser o certo. Protegido duas vezes: na SecurityConfig (/api/admin/**)
 * e aqui com @PreAuthorize (defesa em profundidade).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
