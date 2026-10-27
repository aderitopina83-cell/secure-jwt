package org.nwh.crud.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller minimo: quem abrir a pagina inicial (http://localhost:8080/)
 * e enviado para a lista de artigos.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String inicio() {
        // "redirect:" manda o browser pedir outra URL.
        return "redirect:/artigos";
    }
}
