package org.nwh.crud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PONTO DE ENTRADA da aplicacao. E aqui que tudo comeca.
 *
 * @SpringBootApplication faz tres coisas de uma vez:
 *   1. Diz que esta classe tem configuracoes.
 *   2. Liga a "autoconfiguracao": o Spring ve que tens Thymeleaf, JPA e H2
 *      no pom.xml e configura-os sozinho.
 *   3. Procura automaticamente todas as classes com @Controller, @Service,
 *      @Repository... DENTRO deste pacote (org.nwh.crud) e subpacotes.
 *      Por isso esta classe tem de ficar na pasta "de cima".
 */
@SpringBootApplication
public class CrudApplication {

    /**
     * O metodo main e onde qualquer programa Java comeca (semana 1 do plano).
     * Aqui so pedimos ao Spring para arrancar: ele liga o servidor web na
     * porta 8080, cria a base de dados e prepara os controllers.
     */
    public static void main(String[] args) {
        SpringApplication.run(CrudApplication.class, args);
    }
}
