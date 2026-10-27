# crud-artigos — CRUD de inventário em Java (Spring Boot)

Aplicação web pequena para aprender: criar, ver, editar e apagar artigos de inventário.
Todo o código tem comentários em português a explicar o que cada parte faz.

## Correr

```bash
mvn spring-boot:run      # arranca a app em http://localhost:8080
mvn test                 # corre os 9 testes automáticos
```

No Codespaces: separador **Ports** → porta 8080 → ícone 🌐.
A base de dados é em memória: ao parar a app, os dados voltam aos 5 exemplos.

**Ver a base de dados:** abre `/h2-console`, JDBC URL `jdbc:h2:mem:inventario`, utilizador `sa`, senha vazia.
Experimenta `SELECT * FROM artigo;`

## Como um pedido percorre o código

```
Browser ──► ArtigoController ──► ArtigoService ──► ArtigoRepository ──► Base de dados (H2)
              (HTTP, páginas)     (regras)          (SQL automático)
   ◄────────── lista.html / formulario.html (Thymeleaf) ◄──────────────
```

Exemplo: carregas em **Guardar** num artigo novo.

1. O browser envia `POST /artigos` com os campos do formulário.
2. O Spring cria um `Artigo` e preenche-o com os setters (`setCodigo`, ...).
3. `@Valid` verifica as regras da classe `Artigo` (`@NotBlank`, `@Min`, ...).
4. `ArtigoController.criar()`: se houver erros, mostra o formulário outra vez.
5. `ArtigoService.criar()`: confirma que o código não está repetido.
6. `ArtigoRepository.save()`: o Hibernate faz o `INSERT` (vês o SQL no terminal).
7. O controller responde com *redirect* para `/artigos` e a mensagem verde.

## Ordem de leitura sugerida

| # | Ficheiro | O que aprendes | Semana do plano |
|---|---|---|---|
| 1 | `model/Area.java` | enum | 4 |
| 2 | `model/Artigo.java` | classe, campos privados, getters/setters, anotações | 4 |
| 3 | `repository/ArtigoRepository.java` | interface; consultas a partir do nome do método | 5 |
| 4 | `service/*Exception.java` | exceções próprias | 5 |
| 5 | `service/ArtigoService.java` | regras de negócio, as 4 operações CRUD | 4–5 |
| 6 | `controller/ArtigoController.java` | rotas, formulários, validação, redirect | Fase 5 |
| 7 | `templates/artigos/*.html` | Thymeleaf (como o Blade) | Fase 5 |
| 8 | `test/.../ArtigoControllerTest.java` | testes automáticos | Fase 2 |

## Laravel → Spring Boot neste projeto

| Laravel | Aqui |
|---|---|
| `routes/web.php` + Controller | `@GetMapping` / `@PostMapping` no `ArtigoController` |
| Model Eloquent | `@Entity` `Artigo` |
| `Artigo::where(...)->get()` | métodos do `ArtigoRepository` |
| Form Request `rules()` | `@NotBlank`, `@Min`, ... no `Artigo` + `@Valid` |
| `@foreach` / `{{ }}` no Blade | `th:each` / `th:text` no Thymeleaf |
| `redirect()->with('msg', ...)` | `redirect.addFlashAttribute(...)` + `"redirect:/artigos"` |
| Seeder | `config/DadosExemplo.java` |
| `public/` | `src/main/resources/static/` |

## Exercícios (faz tu, depois pede-me revisão)

1. **Fácil:** acrescenta a área `UCI("Cuidados Intensivos")` ao enum `Area`. Repara que aparece sozinha no formulário.
2. **Fácil:** muda a mensagem de erro da quantidade negativa.
3. **Médio:** acrescenta o campo `fornecedor` (texto, opcional, máx. 80) ao `Artigo`, ao formulário, à lista e ao `atualizar()` do serviço.
4. **Médio:** cria a página `/artigos/repor` que só mostra os artigos que precisam de reposição (dica: um método novo no repositório ou um filtro com Streams no serviço).
5. **Médio:** escreve um teste novo que confirma que uma quantidade com letras dá erro.
6. **Desafio:** acrescenta "Retirar stock": um botão em cada linha que pede uma quantidade e subtrai, recusando ficar negativo (com uma exceção própria `StockInsuficienteException`).

## Para uma versão real

Esta app não tem login. Numa versão para usar no trabalho falta: autenticação (Spring Security, como no projeto `secure-jwt`), proteção CSRF, base de dados PostgreSQL com migrações Flyway, e o registo de quem alterou o quê.
