package com.portalsiniestros.api;

import com.portalsiniestros.model.Cliente;
import com.portalsiniestros.model.Poliza;
import com.portalsiniestros.repository.ClienteRepository;
import com.portalsiniestros.repository.PolizaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ClienteRepository clientes;
    private final PolizaRepository polizas;
    private final PasswordEncoder encoder;
    public AdminController(ClienteRepository clientes, PolizaRepository polizas, PasswordEncoder encoder) {
        this.clientes = clientes; this.polizas = polizas; this.encoder = encoder;
    }
    public record ClienteRequest(@NotBlank String nombre, @NotBlank @Email String email,
                                 @NotBlank @Size(min = 8) String password) {}
    public record ClienteResponse(Long id, String nombre, String email) {}
    public record PolizaRequest(@NotNull Long clienteId, @NotBlank String numero,
                                @NotBlank String placa, @NotNull LocalDate inicioVigencia,
                                @NotNull LocalDate finVigencia) {}
    public record PolizaResponse(Long id, String numero, String placa, boolean activa,
                                 LocalDate inicioVigencia, LocalDate finVigencia,
                                 Long clienteId, String clienteNombre, String clienteEmail) {}

    @GetMapping("/clientes")
    public List<ClienteResponse> clientes() {
        return clientes.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(c -> "CLIENTE".equals(c.rolEfectivo()))
                .map(this::clienteResponse).toList();
    }
    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse crearCliente(@Valid @RequestBody ClienteRequest request) {
        String email = request.email().trim().toLowerCase();
        if (clientes.findByEmail(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        Cliente cliente = new Cliente();
        cliente.nombre = request.nombre().trim();
        cliente.email = email;
        cliente.passwordHash = encoder.encode(request.password());
        cliente.rol = "CLIENTE";
        return clienteResponse(clientes.save(cliente));
    }
    @GetMapping("/polizas")
    public List<PolizaResponse> polizas() {
        return polizas.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::polizaResponse).toList();
    }
    @PostMapping("/polizas")
    @ResponseStatus(HttpStatus.CREATED)
    public PolizaResponse crearPoliza(@Valid @RequestBody PolizaRequest request) {
        if (request.finVigencia().isBefore(request.inicioVigencia()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final debe ser posterior a la inicial");
        String numero = request.numero().trim().toUpperCase();
        if (polizas.findByNumero(numero).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El número de póliza ya existe");
        Cliente cliente = clientes.findById(request.clienteId())
                .filter(c -> "CLIENTE".equals(c.rolEfectivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));
        Poliza poliza = new Poliza();
        poliza.cliente = cliente;
        poliza.numero = numero;
        poliza.placa = request.placa().trim().toUpperCase();
        poliza.inicioVigencia = request.inicioVigencia();
        poliza.finVigencia = request.finVigencia();
        poliza.activa = true;
        return polizaResponse(polizas.save(poliza));
    }
    private ClienteResponse clienteResponse(Cliente c) { return new ClienteResponse(c.id, c.nombre, c.email); }
    private PolizaResponse polizaResponse(Poliza p) {
        return new PolizaResponse(p.id, p.numero, p.placa, p.activa, p.inicioVigencia,
                p.finVigencia, p.cliente.id, p.cliente.nombre, p.cliente.email);
    }
}
