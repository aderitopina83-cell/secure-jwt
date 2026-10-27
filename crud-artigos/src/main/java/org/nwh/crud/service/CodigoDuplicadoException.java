package org.nwh.crud.service;

/**
 * Lancada quando se tenta gravar um artigo com um codigo que ja existe.
 * O controller apanha-a e mostra a mensagem por baixo do campo "Código".
 */
public class CodigoDuplicadoException extends RuntimeException {

    public CodigoDuplicadoException(String codigo) {
        super("Já existe um artigo com o código " + codigo);
    }
}
