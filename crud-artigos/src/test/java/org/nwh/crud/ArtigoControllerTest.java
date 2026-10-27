package org.nwh.crud;

import org.junit.jupiter.api.Test;
import org.nwh.crud.model.Area;
import org.nwh.crud.model.Artigo;
import org.nwh.crud.repository.ArtigoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * TESTES AUTOMATICOS do CRUD. Correm com:  mvn test
 *
 * Cada metodo com @Test e um teste independente que:
 *   1. prepara uma situacao,
 *   2. faz um pedido a aplicacao (com MockMvc, sem browser),
 *   3. confirma que o resultado e o esperado.
 *
 * @SpringBootTest        -> arranca a aplicacao completa para o teste
 * @AutoConfigureMockMvc  -> da-nos o MockMvc, um "browser falso"
 * @Transactional         -> no fim de CADA teste, tudo o que foi gravado e
 *                           desfeito. Assim os testes nao interferem uns com os outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArtigoControllerTest {

    // @Autowired pede ao Spring os objetos de que o teste precisa.
    @Autowired
    MockMvc mvc;

    @Autowired
    ArtigoRepository repositorio;

    // ---------- R = READ ----------

    @Test
    void listaMostraOsArtigosDeExemplo() throws Exception {
        mvc.perform(get("/artigos"))
                .andExpect(status().isOk())                          // 200
                .andExpect(view().name("artigos/lista"))             // pagina certa
                .andExpect(content().string(containsString("LUV-075")));
    }

    @Test
    void pesquisaFiltraPorDescricao() throws Exception {
        mvc.perform(get("/artigos").param("q", "sutura"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUT-30V")))
                .andExpect(content().string(not(containsString("LUV-075"))));
    }

    // ---------- C = CREATE ----------

    @Test
    void criarArtigoValidoGravaERedireciona() throws Exception {
        mvc.perform(post("/artigos")
                        .param("codigo", "tst-001")          // minusculas de proposito
                        .param("descricao", "Artigo de teste")
                        .param("area", "OR")
                        .param("quantidade", "10")
                        .param("stockMinimo", "5")
                        .param("dataValidade", "2030-01-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/artigos"))
                .andExpect(flash().attributeExists("mensagem"));

        // Confirma na base de dados. O codigo foi passado a maiusculas pelo setter.
        assertThat(repositorio.existsByCodigo("TST-001")).isTrue();
    }

    @Test
    void criarArtigoInvalidoMostraErros() throws Exception {
        mvc.perform(post("/artigos")
                        .param("codigo", "")
                        .param("descricao", "Sem código")
                        .param("area", "OR")
                        .param("quantidade", "-1")
                        .param("stockMinimo", "5"))
                .andExpect(status().isOk())                          // nao redireciona
                .andExpect(view().name("artigos/formulario"))        // volta ao formulario
                .andExpect(model().attributeHasFieldErrors("artigo", "codigo", "quantidade"))
                .andExpect(content().string(containsString("O código é obrigatório")));
    }

    @Test
    void codigoDuplicadoERecusado() throws Exception {
        mvc.perform(post("/artigos")
                        .param("codigo", "LUV-075")                  // ja existe nos exemplos
                        .param("descricao", "Outra coisa")
                        .param("area", "OR")
                        .param("quantidade", "1")
                        .param("stockMinimo", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("artigos/formulario"))
                .andExpect(model().attributeHasFieldErrors("artigo", "codigo"))
                .andExpect(content().string(containsString("Já existe um artigo com o código LUV-075")));
    }

    // ---------- U = UPDATE ----------

    @Test
    void editarAtualizaOArtigo() throws Exception {
        Artigo artigo = repositorio.save(new Artigo("EDT-001", "Antes", Area.ED, 1, 1, null));

        mvc.perform(post("/artigos/" + artigo.getId())
                        .param("codigo", "EDT-001")                  // o mesmo codigo: permitido
                        .param("descricao", "Depois")
                        .param("area", "ED")
                        .param("quantidade", "99")
                        .param("stockMinimo", "1"))
                .andExpect(redirectedUrl("/artigos"));

        Artigo atualizado = repositorio.findById(artigo.getId()).orElseThrow();
        assertThat(atualizado.getDescricao()).isEqualTo("Depois");
        assertThat(atualizado.getQuantidade()).isEqualTo(99);
    }

    @Test
    void editarArtigoInexistenteDa404() throws Exception {
        mvc.perform(get("/artigos/999999/editar"))
                .andExpect(status().isNotFound());
    }

    // ---------- D = DELETE ----------

    @Test
    void apagarRemoveOArtigo() throws Exception {
        Artigo artigo = repositorio.save(new Artigo("DEL-001", "Para apagar", Area.OR, 1, 1, null));

        mvc.perform(post("/artigos/" + artigo.getId() + "/apagar"))
                .andExpect(redirectedUrl("/artigos"));

        assertThat(repositorio.existsById(artigo.getId())).isFalse();
    }

    // ---------- Seguranca ----------

    @Test
    void textoComHtmlNaoEExecutado() throws Exception {
        repositorio.save(new Artigo("XSS-001", "<script>alert(1)</script>", Area.OR, 1, 1, null));

        // th:text transforma < em &lt; : o browser mostra o texto em vez de o executar.
        mvc.perform(get("/artigos"))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
