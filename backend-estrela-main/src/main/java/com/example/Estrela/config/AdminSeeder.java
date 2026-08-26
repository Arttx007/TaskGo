package com.example.Estrela.config;

import com.example.Estrela.Entity.Administrador;
import com.example.Estrela.repository.AdministradorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Garante que existe pelo menos um administrador ao subir a aplicação, já que não há fluxo de
 * autocadastro de admin no MVP — idempotente: só cria se a tabela estiver vazia.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;
    private final String emailBootstrap;
    private final String senhaBootstrap;

    public AdminSeeder(AdministradorRepository administradorRepository,
                        PasswordEncoder passwordEncoder,
                        @Value("${taskgo.admin.bootstrap-email}") String emailBootstrap,
                        @Value("${taskgo.admin.bootstrap-senha}") String senhaBootstrap) {
        this.administradorRepository = administradorRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailBootstrap = emailBootstrap;
        this.senhaBootstrap = senhaBootstrap;
    }

    @Override
    public void run(String... args) {
        if (administradorRepository.count() == 0) {
            Administrador admin = new Administrador();
            admin.setNome("Administrador TaskGo");
            admin.setEmail(emailBootstrap);
            admin.setSenha(passwordEncoder.encode(senhaBootstrap));
            administradorRepository.save(admin);
        }
    }
}
