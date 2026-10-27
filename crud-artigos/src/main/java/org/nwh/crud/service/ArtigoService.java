package org.nwh.crud.service;

import org.nwh.crud.model.Artigo;
import org.nwh.crud.repository.ArtigoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SERVICO = onde vivem as REGRAS DE NEGOCIO.
 *
 * Porque nao por isto diretamente no controller?
 *   - O controller so trata de HTTP: receber o pedido, escolher a pagina.
 *   - O servico trata das regras: "nao pode haver codigos repetidos".
 * Assim, se amanha criares uma API REST ou importares um CSV, reutilizas
 * as mesmas regras sem as copiar.
 *
 * As 4 operacoes do CRUD estao aqui:
 *   C = Create  -> criar()
 *   R = Read    -> listar() e obter()
 *   U = Update  -> atualizar()
 *   D = Delete  -> apagar()
 *
 * @Service diz ao Spring: "cria UM objeto desta classe e da-o a quem precisar".
 */
@Service
public class ArtigoService {

    /*
     * O servico precisa do repositorio. Nao fazemos "new ArtigoRepository()"
     * (nem podiamos: e uma interface). Pedimo-lo no construtor e o Spring
     * entrega-o automaticamente. Chama-se INJECAO DE DEPENDENCIAS.
     * E igual ao Laravel quando pedes uma classe no construtor do controller.
     */
    private final ArtigoRepository repositorio;

    public ArtigoService(ArtigoRepository repositorio) {
        this.repositorio = repositorio;
    }

    // ===================== R = READ (ler) =====================

    /**
     * Lista os artigos. Se houver texto de pesquisa, filtra por codigo ou
     * descricao; senao, devolve todos.
     *
     * @Transactional(readOnly = true): abre uma transacao so de leitura.
     * E mais rapido e garante que este metodo nunca altera dados por engano.
     */
    @Transactional(readOnly = true)
    public List<Artigo> listar(String pesquisa) {
        // isBlank() = vazio ou so espacos
        if (pesquisa == null || pesquisa.isBlank()) {
            return repositorio.findAllByOrderByCodigoAsc();
        }
        String termo = pesquisa.trim();
        return repositorio.findByCodigoContainingIgnoreCaseOrDescricaoContainingIgnoreCaseOrderByCodigoAsc(
                termo, termo);
    }

    /**
     * Devolve um artigo pelo id, ou lanca excecao se nao existir.
     *
     * findById devolve um Optional: uma "caixa" que pode estar vazia
     * (Fase 2 do plano). orElseThrow = "da-me o que esta na caixa; se estiver
     * vazia, lanca esta excecao". Evita o famoso NullPointerException.
     *
     * "() -> new ..." e uma LAMBDA: uma funcao curta escrita no proprio sitio.
     */
    @Transactional(readOnly = true)
    public Artigo obter(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ArtigoNaoEncontradoException(id));
    }

    // ===================== C = CREATE (criar) =====================

    /**
     * Grava um artigo novo.
     *
     * @Transactional: tudo o que acontece aqui dentro ou corre bem por
     * inteiro, ou e desfeito por inteiro (como DB::transaction no Laravel).
     */
    @Transactional
    public Artigo criar(Artigo artigo) {
        // Regra de negocio: codigos unicos.
        if (repositorio.existsByCodigo(artigo.getCodigo())) {
            throw new CodigoDuplicadoException(artigo.getCodigo());
        }
        // Garantia: um artigo novo nunca traz id. Se alguem tentasse enviar
        // um id no formulario, isto impede que sobrescreva um artigo existente.
        artigo.setId(null);
        return repositorio.save(artigo); // INSERT
    }

    // ===================== U = UPDATE (atualizar) =====================

    /**
     * Atualiza um artigo existente com os dados vindos do formulario.
     *
     * Repara na tecnica: NAO gravamos diretamente o objeto do formulario.
     * Vamos buscar o artigo verdadeiro a base de dados e copiamos so os
     * campos que o utilizador pode alterar. Assim, campos que nao estao no
     * formulario nunca sao apagados ou alterados por engano.
     */
    @Transactional
    public Artigo atualizar(Long id, Artigo dados) {
        Artigo existente = obter(id); // 404 se nao existir

        // Outro artigo (id diferente) ja usa este codigo?
        if (repositorio.existsByCodigoAndIdNot(dados.getCodigo(), id)) {
            throw new CodigoDuplicadoException(dados.getCodigo());
        }

        existente.setCodigo(dados.getCodigo());
        existente.setDescricao(dados.getDescricao());
        existente.setArea(dados.getArea());
        existente.setQuantidade(dados.getQuantidade());
        existente.setStockMinimo(dados.getStockMinimo());
        existente.setDataValidade(dados.getDataValidade());

        // Dentro de uma transacao, o JPA repara sozinho que o objeto mudou
        // e faz o UPDATE no fim. O save() aqui e so para ficar explicito.
        return repositorio.save(existente);
    }

    // ===================== D = DELETE (apagar) =====================

    @Transactional
    public void apagar(Long id) {
        Artigo existente = obter(id); // 404 se nao existir
        repositorio.delete(existente); // DELETE
    }
}
