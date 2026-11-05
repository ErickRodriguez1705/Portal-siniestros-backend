package com.portalsiniestros.api;

import com.portalsiniestros.model.Cliente;
import com.portalsiniestros.repository.ClienteRepository;
import com.portalsiniestros.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ClienteRepository clientes;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    public AuthController(ClienteRepository clientes, PasswordEncoder encoder, JwtService jwt) {
        this.clientes = clientes; this.encoder = encoder; this.jwt = jwt;
    }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record LoginResponse(String token, String nombre, String email, String rol) {}
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Cliente cliente = clientes.findByEmail(request.email()).orElseThrow(this::unauthorized);
        if (!encoder.matches(request.password(), cliente.passwordHash)) throw unauthorized();
        return new LoginResponse(jwt.create(cliente.email), cliente.nombre, cliente.email, cliente.rolEfectivo());
    }
    @GetMapping("/me")
    public LoginResponse me(Authentication auth) {
        Cliente cliente = clientes.findByEmail(auth.getName()).orElseThrow(this::unauthorized);
        return new LoginResponse("", cliente.nombre, cliente.email, cliente.rolEfectivo());
    }
    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }
}
