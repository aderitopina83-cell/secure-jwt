package org.nwh.securejwt.config;

import org.nwh.securejwt.domain.AppUser;
import org.nwh.securejwt.domain.Role;
import org.nwh.securejwt.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * So em dev: cria um admin a partir de variaveis de ambiente.
 * Nao existe senha por defeito no codigo.
 *
 *   DEV_ADMIN_USER=admin@nwh.local DEV_ADMIN_PASSWORD='uma-senha-longa' mvn spring-boot:run
 */
@Component
@Profile("dev")
public class DevUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public DevUserSeeder(AppUserRepository users, PasswordEncoder encoder,
                         @Value("${DEV_ADMIN_USER:}") String username,
                         @Value("${DEV_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (username.isBlank() || password.isBlank()) {
            log.info("DEV_ADMIN_USER / DEV_ADMIN_PASSWORD nao definidos: nenhum utilizador criado.");
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("DEV_ADMIN_PASSWORD precisa de pelo menos 12 caracteres.");
        }
        if (users.findByUsername(AppUser.normalize(username)).isEmpty()) {
            users.save(new AppUser(username, encoder.encode(password), Role.ADMIN));
            log.info("Utilizador admin de desenvolvimento criado: {}", AppUser.normalize(username));
        }
    }
}
