package org.nwh.crud.model;

/**
 * ENUM = uma lista FECHADA de valores possiveis (semana 4 do plano).
 *
 * Um artigo so pode pertencer a uma destas areas. Se alguem tentar gravar
 * "XYZ", o Java nao deixa. E mais seguro do que usar uma String livre,
 * onde "OR", "or" e "O.R." seriam tres valores diferentes.
 */
public enum Area {

    // Cada valor tem um "nome bonito" para mostrar nas paginas.
    // O valor entre parenteses e passado ao construtor, mais abaixo.
    OR("Bloco Operatório"),
    MED_SURG("Med Surg"),
    ED("Urgência"),
    ARMAZEM("Armazém Central");

    // Campo que guarda o nome bonito de cada valor.
    // "final" = depois de definido, nunca muda.
    private final String descricao;

    // Construtor do enum: corre uma vez para cada valor acima.
    Area(String descricao) {
        this.descricao = descricao;
    }

    // Getter: permite ler o nome bonito noutros sitios, ex.: Area.OR.getDescricao()
    public String getDescricao() {
        return descricao;
    }
}
