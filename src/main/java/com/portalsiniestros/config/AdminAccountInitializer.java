package com.portalsiniestros.config;

import com.portalsiniestros.model.Cliente;
import com.portalsiniestros.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminAccountInitializer {
    @Bean CommandLineRunner crearAdministradorInicial(ClienteRepository clientes,
            PasswordEncoder encoder,
            @Value("${app.admin.initial.email:}") String email,
            @Value("${app.admin.initial.password:}") String password,
            @Value("${app.admin.initial.name:Administrador}") String nombre) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.length() < 12) {
                throw new IllegalStateException("Configura ADMIN_INITIAL_EMAIL y una ADMIN_INITIAL_PASSWORD de al menos 12 caracteres");
            }
            String normalizedEmail = email.trim().toLowerCase();
            var existente = clientes.findByEmail(normalizedEmail);
            if (existente.isPresent()) {
                if (!"ADMIN".equals(existente.get().rolEfectivo()))
                    throw new IllegalStateException("El correo del administrador inicial ya pertenece a un cliente");
                return;
            }
            Cliente admin = new Cliente();
            admin.nombre = nombre.trim();
            admin.email = normalizedEmail;
            admin.passwordHash = encoder.encode(password);
            admin.rol = "ADMIN";
            clientes.save(admin);
        };
    }
}
