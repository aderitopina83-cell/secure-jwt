package org.nwh.crud.config;

import org.nwh.crud.model.Area;
import org.nwh.crud.model.Artigo;
import org.nwh.crud.repository.ArtigoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Cria alguns artigos de exemplo quando a aplicacao arranca, para a lista
 * nao comecar vazia. Equivalente a um Seeder do Laravel.
 *
 * CommandLineRunner = o metodo run() corre uma vez, logo depois do arranque.
 * Os dados sao FICTICIOS, apenas para aprender.
 */
@Component
public class DadosExemplo implements CommandLineRunner {

    private final ArtigoRepository repositorio;

    public DadosExemplo(ArtigoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void run(String... args) {
        // So cria os exemplos se a tabela estiver vazia.
        if (repositorio.count() > 0) {
            return;
        }

        LocalDate hoje = LocalDate.now();

        // List.of(...) cria uma lista com estes elementos.
        // Repara nas datas: umas ja expiraram, outras expiram em breve,
        // para veres as etiquetas coloridas na pagina.
        List<Artigo> exemplos = List.of(
                new Artigo("LUV-075", "Luvas cirúrgicas estéreis 7.5", Area.OR, 40, 50, hoje.plusMonths(8)),
                new Artigo("SUT-30V", "Sutura vicryl 3-0", Area.OR, 120, 30, hoje.plusDays(12)),
                new Artigo("GAZ-1010", "Compressas de gaze 10x10", Area.MED_SURG, 300, 100, hoje.plusYears(2)),
                new Artigo("SER-10ML", "Seringa 10 ml", Area.ED, 15, 20, hoje.minusDays(3)),
                new Artigo("BAT-AA", "Pilhas AA", Area.ARMAZEM, 60, 24, null)
        );

        // saveAll grava todos de uma vez.
        repositorio.saveAll(exemplos);
    }
}
