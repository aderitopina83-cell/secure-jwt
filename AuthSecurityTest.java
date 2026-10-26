package org.nwh.securejwt;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.nwh.securejwt.config.SecurityProperties;
import org.nwh.securejwt.domain.AppUser;
import org.nwh.securejwt.domain.Role;
import org.nwh.securejwt.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de ATAQUE: cada teste tenta partir uma camada de seguranca.
 * Corre com:  mvn verify
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityTest {

    private static final String PASSWORD = "Senha-Muito-Longa-2026!";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SecurityProperties props;

    // ---------- utilitarios ----------

    private AppUser newUser(Role role) {
        return users.save(new AppUser("u-" + UUID.randomUUID() + "@nwh.local", encoder.encode(PASSWORD), role));
    }

    /** Cada teste usa um IP diferente para nao interferir no limite por IP. */
    private static String freshIp() {
        return "10.0." + (IP_COUNTER.get() / 250) + "." + (IP_COUNTER.getAndIncrement() % 250 + 1);
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder b, String ip) {
        return b.with(request -> {
            request.setRemoteAddr(ip);
            request.setSecure(true);
            return request;
        });
    }

    private MockHttpServletResponse login(String username, String password, String ip) throws Exception {
        return mvc.perform(from(post("/api/auth/login"), ip)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String validToken(AppUser user) throws Exception {
        MockHttpServletResponse r = login(user.getUsername(), PASSWORD, freshIp());
        assertThat(r.getStatus()).isEqualTo(200);
        return r.getCookie("__Host-access").getValue();
    }

    // ---------- passos 1-4: login ----------

    @Test
    void loginDevolveCookiesSegurosESemTokenNoCorpo() throws Exception {
        AppUser user = newUser(Role.USER);
        MockHttpServletResponse r = login(user.getUsername(), PASSWORD, freshIp());

        assertThat(r.getStatus()).isEqualTo(200);
        String access = r.getCookie("__Host-access").getValue();
        assertThat(access).isNotBlank();
        assertThat(r.getContentAsString()).doesNotContain(access);

        List<String> setCookies = r.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).filteredOn(c -> c.startsWith("__Host-access=") || c.startsWith("__Secure-refresh="))
                .hasSize(2)
                .allSatisfy(c -> assertThat(c).contains("HttpOnly").contains("Secure").contains("SameSite=Strict"));
        assertThat(setCookies).filteredOn(c -> c.startsWith("__Secure-refresh="))
                .allSatisfy(c -> assertThat(c).contains("Path=/api/auth"));
    }

    @Test
    void senhaErradaEUtilizadorInexistenteDaoAMesmaResposta() throws Exception {
        AppUser user = newUser(Role.USER);
        MockHttpServletResponse wrongPassword = login(user.getUsername(), "errada-errada-errada", freshIp());
        MockHttpServletResponse unknownUser = login("ninguem@nwh.local", "errada-errada-errada", freshIp());

        assertThat(wrongPassword.getStatus()).isEqualTo(401);
        assertThat(unknownUser.getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownUser.getContentAsString());
    }

    @Test
    void forcaBrutaBloqueiaAConta() throws Exception {
        AppUser user = newUser(Role.USER);
        for (int i = 0; i < props.maxFailedLogins(); i++) {
            login(user.getUsername(), "tentativa-" + i, freshIp());
        }
        // Mesmo com a senha CERTA, a conta esta bloqueada
        assertThat(login(user.getUsername(), PASSWORD, freshIp()).getStatus()).isEqualTo(401);
    }

    @Test
    void limitePorIpDevolve429() throws Exception {
        String ip = freshIp();
        int last = 0;
        for (int i = 0; i <= props.maxLoginRequestsPerIpPerMinute(); i++) {
            last = login("x" + i + "@nwh.local", "qualquer-coisa", ip).getStatus();
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    void fluxoCsrfRealDoBrowserFunciona() throws Exception {
        AppUser user = newUser(Role.USER);
        String ip = freshIp();
        // 1. O frontend pede o cookie XSRF-TOKEN
        MockHttpServletResponse csrfResponse = mvc.perform(from(get("/api/auth/csrf"), ip))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrfResponse.getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        // 2. Devolve-o no cabecalho X-XSRF-TOKEN junto com o login
        mvc.perform(from(post("/api/auth/login"), ip)
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void loginSemCsrfERecusado() throws Exception {
        AppUser user = newUser(Role.USER);
        mvc.perform(from(post("/api/auth/login"), freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- passos 5-8: acesso protegido ----------

    @Test
    void semTokenDa401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoPorCookieEPorCabecalhoFuncionam() throws Exception {
        AppUser user = newUser(Role.USER);
        String token = validToken(user);

        mvc.perform(get("/api/me").cookie(new Cookie("__Host-access", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenNaUrlERecusado() throws Exception {
        String token = validToken(newUser(Role.USER));
        mvc.perform(get("/api/me").param("access_token", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAdulteradoERecusado() throws Exception {
        AppUser user = newUser(Role.USER);
        String[] parts = validToken(user).split("\\.");
        String fakePayload = b64("{\"sub\":\"" + user.getId() + "\",\"roles\":[\"ADMIN\"],\"iss\":\"" + props.issuer()
                + "\",\"aud\":[\"" + props.audience() + "\"],\"jti\":\"x\",\"iat\":" + Instant.now().getEpochSecond()
                + ",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond() + "}");
        String forged = parts[0] + "." + fakePayload + "." + parts[2];

        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void algNoneERecusado() throws Exception {
        AppUser admin = newUser(Role.ADMIN);
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"" + admin.getId() + "\",\"roles\":[\"ADMIN\"],\"iss\":\"" + props.issuer()
                + "\",\"aud\":[\"" + props.audience() + "\"],\"jti\":\"" + UUID.randomUUID() + "\",\"iat\":"
                + Instant.now().getEpochSecond() + ",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond() + "}");

        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + header + "." + payload + "."))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confusaoDeAlgoritmoHs256ERecusada() throws Exception {
        AppUser admin = newUser(Role.ADMIN);
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"" + admin.getId() + "\",\"roles\":[\"ADMIN\"],\"iss\":\"" + props.issuer()
                + "\",\"aud\":[\"" + props.audience() + "\"],\"jti\":\"" + UUID.randomUUID() + "\",\"iat\":"
                + Instant.now().getEpochSecond() + ",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond() + "}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("qualquer-segredo-que-o-atacante-escolha-32b".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + header + "." + payload + "." + signature))
                .andExpect(status().isUnauthorized());
    }

    private String signed(AppUser user, Instant iat, Instant exp, String audience, String issuer) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).subject(user.getId().toString())
                .issuedAt(iat).expiresAt(exp).id(UUID.randomUUID().toString())
                .claim("roles", List.of(user.getRole().name())).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void tokenExpiradoERecusado() throws Exception {
        AppUser user = newUser(Role.USER);
        String token = signed(user, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(600),
                props.audience(), props.issuer());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void audienceOuIssuerErradosSaoRecusados() throws Exception {
        AppUser user = newUser(Role.USER);
        Instant now = Instant.now();
        String wrongAud = signed(user, now, now.plusSeconds(300), "outra-api", props.issuer());
        String wrongIss = signed(user, now, now.plusSeconds(300), props.audience(), "https://atacante.example");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAud))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIss))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void utilizadorNormalNaoEntraNaAreaAdmin() throws Exception {
        String token = validToken(newUser(Role.USER));
        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEntraNaAreaAdmin() throws Exception {
        String token = validToken(newUser(Role.ADMIN));
        mvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void contaDesativadaPerdeAcessoImediatamente() throws Exception {
        AppUser user = newUser(Role.USER);
        String token = validToken(user);
        AppUser reloaded = users.findById(user.getId()).orElseThrow();
        reloaded.setEnabled(false);
        users.save(reloaded);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- refresh, logout ----------

    @Test
    void refreshRodaEReutilizacaoRevogaTudo() throws Exception {
        AppUser user = newUser(Role.USER);
        String ip = freshIp();
        MockHttpServletResponse loginResponse = login(user.getUsername(), PASSWORD, ip);
        String refresh1 = loginResponse.getCookie("__Secure-refresh").getValue();

        // 1a utilizacao: ok, devolve um token novo
        MockHttpServletResponse r1 = mvc.perform(from(post("/api/auth/refresh"), ip).with(csrf())
                        .cookie(new Cookie("__Secure-refresh", refresh1)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String refresh2 = r1.getCookie("__Secure-refresh").getValue();
        String access2 = r1.getCookie("__Host-access").getValue();
        assertThat(refresh2).isNotEqualTo(refresh1);

        // Atacante reutiliza o token antigo: recusado
        mvc.perform(from(post("/api/auth/refresh"), ip).with(csrf())
                        .cookie(new Cookie("__Secure-refresh", refresh1)))
                .andExpect(status().isUnauthorized());

        // Consequencia: o token novo tambem morreu, e o access token tambem
        mvc.perform(from(post("/api/auth/refresh"), ip).with(csrf())
                        .cookie(new Cookie("__Secure-refresh", refresh2)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevogaOAccessTokenNoServidor() throws Exception {
        AppUser user = newUser(Role.USER);
        String ip = freshIp();
        MockHttpServletResponse loginResponse = login(user.getUsername(), PASSWORD, ip);
        String access = loginResponse.getCookie("__Host-access").getValue();
        String refresh = loginResponse.getCookie("__Secure-refresh").getValue();

        mvc.perform(from(post("/api/auth/logout"), ip).with(csrf())
                        .cookie(new Cookie("__Host-access", access), new Cookie("__Secure-refresh", refresh)))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        // Mesmo que alguem tenha copiado o token antes do logout, ja nao serve
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mvc.perform(from(post("/api/auth/refresh"), ip).with(csrf())
                        .cookie(new Cookie("__Secure-refresh", refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pedidoComCookieSemCsrfERecusado() throws Exception {
        String token = validToken(newUser(Role.USER));
        mvc.perform(post("/api/session/logout-all").cookie(new Cookie("__Host-access", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginaDeTesteAbreMesmoComCookieExpirado() throws Exception {
        mvc.perform(get("/index.html").cookie(new Cookie("__Host-access", "token.invalido.ou-expirado")))
                .andExpect(status().isOk());
    }

    @Test
    void cabecalhosDeSegurancaPresentes() throws Exception {
        mvc.perform(from(get("/api/me"), freshIp()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }
}
