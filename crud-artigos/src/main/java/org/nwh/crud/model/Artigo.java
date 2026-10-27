package org.nwh.crud.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * ENTIDADE = uma classe Java que corresponde a uma TABELA da base de dados.
 * Equivalente a um Model do Eloquent no Laravel.
 *
 * Cada objeto Artigo = uma LINHA da tabela "artigo".
 * Cada campo (codigo, descricao...) = uma COLUNA.
 *
 * As anotacoes (palavras com @) sao "etiquetas" que dao instrucoes:
 *   - as de jakarta.persistence dizem como guardar na base de dados;
 *   - as de jakarta.validation dizem que valores sao aceites no formulario.
 */
@Entity                 // "esta classe e uma tabela"
@Table(name = "artigo") // nome da tabela (sem isto, seria o nome da classe)
public class Artigo {

    // ---------------------------------------------------------------
    // CAMPOS (colunas da tabela)
    // Sao "private": so esta classe lhes mexe diretamente.
    // O resto do programa usa os getters e setters (encapsulamento, semana 4).
    // ---------------------------------------------------------------

    /**
     * Chave primaria. @GeneratedValue = a base de dados escolhe o numero
     * sozinha (1, 2, 3...), como o "id auto-increment" das migrations Laravel.
     * Usamos Long (com L maiuscula) e nao long: um artigo novo ainda nao tem
     * id, e Long pode ser null, enquanto long seria 0.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Codigo do artigo, ex.: "LUV-075".
     *  @NotBlank  -> obrigatorio, nao pode ser so espacos
     *  @Pattern   -> so letras maiusculas, numeros e hifen, entre 3 e 20 caracteres
     *  unique     -> a base de dados recusa dois artigos com o mesmo codigo
     */
    @NotBlank(message = "O código é obrigatório")
    @Pattern(regexp = "[A-Z0-9-]{3,20}",
             message = "Use 3 a 20 caracteres: letras maiúsculas, números ou hífen")
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 120, message = "A descrição pode ter no máximo 120 caracteres")
    @Column(nullable = false, length = 120)
    private String descricao;

    /**
     * @Enumerated(EnumType.STRING) guarda o texto "OR" na base de dados.
     * Sem isto, guardaria a POSICAO (0, 1, 2...), e se um dia mudasses a ordem
     * dos valores no enum, os dados antigos ficavam todos errados.
     */
    @NotNull(message = "Escolha uma área")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area;

    // Integer (e nao int) para o formulario poder chegar vazio e mostrarmos
    // a mensagem "obrigatoria" em vez de um erro tecnico.
    @NotNull(message = "A quantidade é obrigatória")
    @Min(value = 0, message = "A quantidade não pode ser negativa")
    @Max(value = 100000, message = "Quantidade demasiado alta")
    @Column(nullable = false)
    private Integer quantidade;

    @NotNull(message = "O stock mínimo é obrigatório")
    @Min(value = 0, message = "O stock mínimo não pode ser negativo")
    @Max(value = 100000, message = "Stock mínimo demasiado alto")
    @Column(nullable = false)
    private Integer stockMinimo;

    /**
     * Data de validade (opcional: alguns artigos nao expiram).
     * LocalDate = so a data, sem horas.
     * @DateTimeFormat diz ao Spring que o formulario envia "2026-12-31"
     * (o formato do <input type="date"> do HTML).
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataValidade;

    // ---------------------------------------------------------------
    // CONSTRUTORES
    // ---------------------------------------------------------------

    /**
     * Construtor vazio. E OBRIGATORIO para o JPA e para o Spring: eles criam
     * primeiro um Artigo vazio e depois preenchem os campos com os setters.
     */
    public Artigo() {
    }

    /** Construtor com tudo: util para criar dados de exemplo em codigo. */
    public Artigo(String codigo, String descricao, Area area,
                  Integer quantidade, Integer stockMinimo, LocalDate dataValidade) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.area = area;
        this.quantidade = quantidade;
        this.stockMinimo = stockMinimo;
        this.dataValidade = dataValidade;
    }

    // ---------------------------------------------------------------
    // REGRAS DE NEGOCIO
    // A classe nao guarda so dados: tambem sabe responder a perguntas
    // sobre si propria. As paginas HTML chamam estes metodos.
    // ---------------------------------------------------------------

    /** true se a quantidade esta no minimo ou abaixo dele. */
    public boolean precisaReposicao() {
        return quantidade != null && stockMinimo != null && quantidade <= stockMinimo;
    }

    /** true se ja passou a data de validade. */
    public boolean expirado() {
        return dataValidade != null && dataValidade.isBefore(LocalDate.now());
    }

    /** true se expira nos proximos 30 dias (mas ainda nao expirou). */
    public boolean expiraEmBreve() {
        return dataValidade != null
                && !expirado()
                && !dataValidade.isAfter(LocalDate.now().plusDays(30));
    }

    // ---------------------------------------------------------------
    // GETTERS E SETTERS
    // O Thymeleaf usa-os para ler (get) e o Spring para preencher (set)
    // os campos a partir do formulario. Sem eles, os campos private
    // seriam invisiveis para o resto do programa.
    // ---------------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    /**
     * Este setter faz mais do que guardar: tira os espacos e passa a
     * maiusculas. Assim "  luv-075 " vira "LUV-075" antes da validacao,
     * e nunca ha dois codigos que so diferem em maiusculas/minusculas.
     * E uma vantagem dos setters: um sitio unico para a regra.
     */
    public void setCodigo(String codigo) {
        this.codigo = (codigo == null) ? null : codigo.trim().toUpperCase();
    }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) {
        this.descricao = (descricao == null) ? null : descricao.trim();
    }

    public Area getArea() { return area; }
    public void setArea(Area area) { this.area = area; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }

    public LocalDate getDataValidade() { return dataValidade; }
    public void setDataValidade(LocalDate dataValidade) { this.dataValidade = dataValidade; }
}
