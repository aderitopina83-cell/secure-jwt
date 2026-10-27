package org.nwh.crud.controller;

import jakarta.validation.Valid;
import org.nwh.crud.model.Area;
import org.nwh.crud.model.Artigo;
import org.nwh.crud.service.ArtigoService;
import org.nwh.crud.service.CodigoDuplicadoException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * CONTROLLER = recebe os pedidos do browser e decide que pagina mostrar.
 * Junta o routes/web.php e o Controller do Laravel num so sitio:
 * a rota e escrita por cima de cada metodo (@GetMapping, @PostMapping).
 *
 * Mapa das rotas deste CRUD:
 *
 *   Metodo  URL                      Metodo Java     O que faz
 *   ------  -----------------------  --------------  ------------------------
 *   GET     /artigos?q=...           listar()        lista + pesquisa   (Read)
 *   GET     /artigos/novo            novo()          formulario vazio
 *   POST    /artigos                 criar()         grava o novo       (Create)
 *   GET     /artigos/{id}/editar     editar()        formulario preenchido
 *   POST    /artigos/{id}            atualizar()     grava alteracoes   (Update)
 *   POST    /artigos/{id}/apagar     apagar()        apaga              (Delete)
 *
 * Os formularios HTML so sabem fazer GET e POST, por isso o Update e o
 * Delete usam POST com URLs diferentes.
 *
 * @Controller        -> "esta classe responde a pedidos e devolve paginas HTML"
 * @RequestMapping    -> todas as rotas desta classe comecam por /artigos
 */
@Controller
@RequestMapping("/artigos")
public class ArtigoController {

    private final ArtigoService servico;

    // Injecao de dependencias: o Spring entrega o servico ao construtor.
    public ArtigoController(ArtigoService servico) {
        this.servico = servico;
    }

    /**
     * @ModelAttribute num metodo: o valor devolvido fica disponivel em TODAS
     * as paginas deste controller com o nome "areas".
     * Usado para preencher a lista <select> das areas no formulario.
     */
    @ModelAttribute("areas")
    public Area[] areas() {
        return Area.values(); // todos os valores do enum: OR, MED_SURG, ED, ARMAZEM
    }

    // ===================== R = READ: listar =====================

    /**
     * GET /artigos           -> todos os artigos
     * GET /artigos?q=luvas   -> so os que tem "luvas" no codigo ou descricao
     *
     * @RequestParam le o "?q=..." da URL. required = false: pode nao vir.
     * Model e a "mochila" de dados que levamos para a pagina HTML.
     * O metodo devolve o NOME da pagina: "artigos/lista" ->
     *   src/main/resources/templates/artigos/lista.html
     */
    @GetMapping
    public String listar(@RequestParam(name = "q", required = false) String q, Model model) {
        model.addAttribute("artigos", servico.listar(q));
        model.addAttribute("q", q); // para o campo de pesquisa manter o texto
        return "artigos/lista";
    }

    // ===================== C = CREATE: mostrar formulario vazio =====================

    @GetMapping("/novo")
    public String novo(Model model) {
        // Um Artigo vazio: o formulario liga cada campo a este objeto.
        model.addAttribute("artigo", new Artigo());
        return "artigos/formulario";
    }

    // ===================== C = CREATE: gravar =====================

    /**
     * POST /artigos (o formulario foi submetido).
     *
     * O que o Spring faz ANTES de entrar neste metodo:
     *   1. Cria um Artigo vazio e preenche-o com os campos do formulario
     *      (pelos setters). Isto chama-se "binding".
     *   2. @Valid: corre as validacoes da classe Artigo (@NotBlank, @Min...).
     *   3. Guarda os erros encontrados no BindingResult.
     *
     * IMPORTANTE: o BindingResult tem de vir LOGO A SEGUIR ao parametro @Valid.
     *
     * Depois de gravar com sucesso, fazemos REDIRECT em vez de mostrar logo
     * a lista. Chama-se padrao POST-Redirect-GET: se o utilizador carregar
     * em F5, o browser repete o GET da lista e nao volta a submeter o
     * formulario (o que criaria um artigo repetido).
     */
    @PostMapping
    public String criar(@Valid @ModelAttribute("artigo") Artigo artigo,
                        BindingResult erros,
                        RedirectAttributes redirect) {
        // Ha erros de validacao? Volta a mostrar o formulario.
        // O Thymeleaf mostra cada mensagem por baixo do campo certo.
        if (erros.hasErrors()) {
            return "artigos/formulario";
        }

        try {
            servico.criar(artigo);
        } catch (CodigoDuplicadoException e) {
            // Erro de negocio: associamo-lo ao campo "codigo" do formulario,
            // tal como um erro de validacao normal.
            erros.rejectValue("codigo", "duplicado", e.getMessage());
            return "artigos/formulario";
        }

        // "Flash attribute": uma mensagem que sobrevive a UM redirect e depois
        // desaparece. Igual ao session()->flash() / with() do Laravel.
        redirect.addFlashAttribute("mensagem", "Artigo " + artigo.getCodigo() + " criado.");
        return "redirect:/artigos";
    }

    // ===================== U = UPDATE: mostrar formulario preenchido =====================

    /**
     * GET /artigos/5/editar
     * @PathVariable le o {id} da URL. Aqui, id = 5.
     * Se o artigo nao existir, o servico lanca ArtigoNaoEncontradoException
     * e o browser recebe um 404.
     */
    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("artigo", servico.obter(id));
        return "artigos/formulario"; // o MESMO formulario do "novo"
    }

    // ===================== U = UPDATE: gravar alteracoes =====================

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("artigo") Artigo artigo,
                            BindingResult erros,
                            RedirectAttributes redirect) {
        // O formulario nao envia o id (vem na URL). Pomo-lo no objeto para,
        // se houver erros, a pagina saber que esta a EDITAR e nao a criar.
        artigo.setId(id);

        if (erros.hasErrors()) {
            return "artigos/formulario";
        }

        try {
            servico.atualizar(id, artigo);
        } catch (CodigoDuplicadoException e) {
            erros.rejectValue("codigo", "duplicado", e.getMessage());
            return "artigos/formulario";
        }

        redirect.addFlashAttribute("mensagem", "Artigo " + artigo.getCodigo() + " atualizado.");
        return "redirect:/artigos";
    }

    // ===================== D = DELETE: apagar =====================

    /**
     * POST /artigos/5/apagar
     * Apagar e sempre POST, nunca GET. Um link GET pode ser aberto por
     * engano (ou por um robo que segue links) e apagaria dados.
     */
    @PostMapping("/{id}/apagar")
    public String apagar(@PathVariable Long id, RedirectAttributes redirect) {
        // Vamos buscar o codigo antes de apagar, so para a mensagem.
        String codigo = servico.obter(id).getCodigo();
        servico.apagar(id);
        redirect.addFlashAttribute("mensagem", "Artigo " + codigo + " apagado.");
        return "redirect:/artigos";
    }
}
