package org.nwh.crud.repository;

import org.nwh.crud.model.Artigo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * REPOSITORIO = a camada que fala com a base de dados.
 *
 * Repara: e uma INTERFACE sem codigo nenhum (semana 5 do plano).
 * O Spring le esta interface e ESCREVE O CODIGO SOZINHO quando a app arranca.
 *
 * Ao estender JpaRepository<Artigo, Long> ganhamos de borla:
 *   findAll()       -> SELECT * FROM artigo
 *   findById(id)    -> SELECT * FROM artigo WHERE id = ?
 *   save(artigo)    -> INSERT (se for novo) ou UPDATE (se ja existir)
 *   deleteById(id)  -> DELETE FROM artigo WHERE id = ?
 *   count()         -> SELECT COUNT(*) FROM artigo
 *
 * <Artigo, Long> = "repositorio de Artigos, cuja chave primaria e um Long".
 * Os < > sao "generics" (Fase 2 do plano).
 */
public interface ArtigoRepository extends JpaRepository<Artigo, Long> {

    /*
     * METODOS POR NOME: o Spring traduz o NOME do metodo para SQL.
     * Le-se como uma frase em ingles:
     *
     *   findBy                        -> SELECT ... WHERE
     *   CodigoContainingIgnoreCase    -> codigo LIKE %texto% (sem distinguir maiusculas)
     *   Or                            -> OR
     *   DescricaoContainingIgnoreCase -> descricao LIKE %texto%
     *   OrderByCodigoAsc              -> ORDER BY codigo ASC
     *
     * O primeiro parametro substitui o primeiro "Containing", o segundo o segundo.
     * O Spring usa sempre parametros (?), por isso nao ha risco de SQL injection.
     */
    List<Artigo> findByCodigoContainingIgnoreCaseOrDescricaoContainingIgnoreCaseOrderByCodigoAsc(
            String codigo, String descricao);

    /** Todos os artigos ordenados por codigo. */
    List<Artigo> findAllByOrderByCodigoAsc();

    /** Ja existe algum artigo com este codigo? -> SELECT COUNT(*) > 0 ... */
    boolean existsByCodigo(String codigo);

    /**
     * Ja existe OUTRO artigo (com id diferente) com este codigo?
     * Usado ao EDITAR: o proprio artigo tem esse codigo e isso nao e um erro.
     */
    boolean existsByCodigoAndIdNot(String codigo, Long id);
}
