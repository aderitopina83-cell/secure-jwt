# secure-jwt — Autenticação JWT segura com Spring Boot

Implementação do fluxo do diagrama (login → JWT assinado → pedido com token → verificação)
com Java 21, Spring Boot 3.5 e Spring Security 6.

## Correr

```bash
# Desenvolvimento (H2 em memória, chaves temporárias)
DEV_ADMIN_USER=admin@nwh.local DEV_ADMIN_PASSWORD='uma-senha-bem-longa' mvn spring-boot:run

# Testes de ataque (22 testes)
mvn verify
```

## Testar no browser

**Local:** depois de `mvn spring-boot:run`, abre http://localhost:8080 (página de teste com botões).

**Online, sem instalar nada (GitHub Codespaces):**
1. Cria um repositório **privado** no GitHub e carrega todos os ficheiros desta pasta (incluindo `.devcontainer/`).
2. No repositório: **Code → Codespaces → Create codespace on main**. Espera 2-3 min.
3. No terminal do Codespace: `mvn verify` (testes) e depois `mvn spring-boot:run`.
4. Aparece "Open in Browser" para a porta 8080. Utilizador `admin@nwh.local`, senha `Senha-Teste-Longa-2026` (só de teste, definida em `.devcontainer/devcontainer.json`).
5. No fim: **Stop codespace** para não gastar horas gratuitas.

## Mapa: passo do diagrama → proteção

| Passo | Proteção | Onde |
|---|---|---|
| 1 Login | Argon2id; limite por IP (429); bloqueio da conta após 5 falhas; mesma resposta e mesmo tempo exista ou não o utilizador; limite de tamanho da senha | `AuthService`, `LoginRateLimiter` |
| 2 Pedido | Só HTTPS (HSTS); CSRF obrigatório | `SecurityConfig` |
| 3 Criar/assinar | **RS256** (privada assina, pública verifica); `iss aud sub iat nbf exp jti ver`; vida de 10 min; sem dados pessoais no token | `TokenService`, `JwtConfig` |
| 4 Devolver | Cookie `__Host-access` **HttpOnly + Secure + SameSite=Strict**; token **não** vai no corpo; refresh token opaco só enviado para `/api/auth` | `AuthCookies`, `AuthController` |
| 6 Pedido com token | Cabeçalho `Bearer` (apps/servidores) ou cookie (browser); token na URL é recusado | `CookieOrHeaderBearerTokenResolver` |
| 7 Verificar | Algoritmo fixo (bloqueia `alg:none` e confusão RS256→HS256); `exp/nbf` com 30 s de tolerância; `iss`; `aud`; claims obrigatórias; **jti revogado?**; **utilizador ativo?**; **versão do token atual?** | `JwtConfig`, `TokenRevocationValidator` |
| 8 Resposta | Autorização por papel (`/api/admin/**` + `@PreAuthorize`); CSP, X-Frame-Options, nosniff, Referrer-Policy; erros sem stack trace | `SecurityConfig`, `AdminController` |
| Refresh | Rotação a cada uso; reutilização de token antigo = roubo → revoga família + todas as sessões | `RefreshTokenService` |
| Logout | Revogação real no servidor (não só apagar cookie); "terminar todas as sessões" | `AuthService` |
| Auditoria | Login, falhas, bloqueios, reutilização de refresh, logout; sem senhas/tokens; anti-injeção de log | `AuditLog` |

## Frontend (JavaScript)

```js
// 1. Obter o cookie XSRF-TOKEN (uma vez ao abrir a app)
await fetch('/api/auth/csrf', { credentials: 'same-origin' });

const xsrf = () => document.cookie.split('; ')
  .find(c => c.startsWith('XSRF-TOKEN='))?.split('=')[1];

// 2. Login: o token chega em cookie HttpOnly, o JS nunca lhe toca
await fetch('/api/auth/login', {
  method: 'POST',
  credentials: 'same-origin',
  headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf() },
  body: JSON.stringify({ username, password })
});

// 3. Pedidos normais: o browser envia o cookie sozinho
const me = await fetch('/api/me', { credentials: 'same-origin' });

// 4. Se der 401, renovar e repetir uma vez
await fetch('/api/auth/refresh', {
  method: 'POST', credentials: 'same-origin', headers: { 'X-XSRF-TOKEN': xsrf() }
});
```

Nunca guardar o token em `localStorage` / `sessionStorage`.

## Produção

1. **Gerar chaves** (fora do Git, ex.: `/run/secrets/`):
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
   openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
   chmod 400 jwt-private.pem
   ```
2. **Variáveis de ambiente** (`SPRING_PROFILES_ACTIVE=prod`):
   `DB_URL DB_USER DB_PASSWORD JWT_ISSUER JWT_KEY_ID JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt-public.pem`
3. **HTTPS** num proxy (nginx/IIS/load balancer) com TLS 1.2+; a app só escuta em rede interna.
4. **PostgreSQL** com `sslmode=verify-full` e utilizador da BD só com permissões nesta schema.
5. **Rotação de chaves**: gerar par novo, mudar `JWT_KEY_ID`; tokens antigos expiram em ≤10 min.
6. **Várias instâncias**: mover o limite por IP para Redis (Bucket4j) ou para o WAF/proxy.
7. **Dependências**: `mvn org.owasp:dependency-check-maven:check` regularmente.
8. **SSO**: se o hospital usar Entra ID/Okta, o login (passos 1-4) passa para lá e esta app fica só com os passos 6-8.
9. Antes de produção: aprovação do IT/segurança da NWH (e HIPAA se houver dados de pacientes).

## Decisões e limites conhecidos

- **Dois separadores a renovar ao mesmo tempo** com o mesmo refresh token: o segundo é tratado como reutilização e termina a sessão (por segurança). O frontend deve serializar os pedidos de refresh.
- A verificação de revogação faz uma consulta à BD por pedido. Para volumes altos, pôr em cache (Caffeine/Redis) com TTL curto.
- O schema vem de migrações Flyway (`db/migration`); `ddl-auto` está desligado.
