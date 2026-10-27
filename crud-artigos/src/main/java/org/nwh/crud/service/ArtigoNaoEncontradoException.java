package org.nwh.crud.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * EXCECAO PROPRIA (semana 5 do plano): lancada quando se pede um artigo que nao existe,
 * por exemplo /artigos/999/editar.
 *
 * "extends RuntimeException" = e uma excecao que nao obriga a ter try/catch
 * em todo o lado.
 *
 * @ResponseStatus(NOT_FOUND): se ninguem a apanhar, o Spring responde ao
 * browser com o codigo 404 (pagina nao encontrada) em vez de 500 (erro interno).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ArtigoNaoEncontradoException extends RuntimeException {

    public ArtigoNaoEncontradoException(Long id) {
        // super(...) chama o construtor da classe-mae (RuntimeException)
        // e guarda a mensagem de erro.
        super("Artigo não encontrado: " + id);
    }
}
