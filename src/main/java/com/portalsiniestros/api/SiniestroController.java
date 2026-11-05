package com.portalsiniestros.api;

import com.portalsiniestros.model.*;
import com.portalsiniestros.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SiniestroController {
    private final ClienteRepository clientes;
    private final PolizaRepository polizas;
    private final SiniestroRepository siniestros;
    private final DetalleRepository detalles;
    public SiniestroController(ClienteRepository clientes, PolizaRepository polizas,
                               SiniestroRepository siniestros, DetalleRepository detalles) {
        this.clientes = clientes; this.polizas = polizas;
        this.siniestros = siniestros; this.detalles = detalles;
    }
    public record PolizaResponse(String numero, String placa) {}
    public record CrearSiniestroRequest(
        @NotBlank String numeroPoliza,
        @NotNull @PastOrPresent LocalDateTime fechaAccidente,
        @NotBlank String ubicacion,
        @NotBlank @Size(min = 20, max = 5000) String descripcion,
        @NotNull @Size(max = 5) List<@NotBlank String> fotos,
        Map<String, String> metadatos) {}
    public record SiniestroResponse(Long id, String numeroPoliza, String placa,
        LocalDateTime fechaAccidente, String ubicacion, String estado,
        String descripcion, List<String> fotos, Map<String, String> metadatos,
        String observacionDecision, Instant fechaDecision) {}

    @GetMapping("/polizas")
    public List<PolizaResponse> polizas(Authentication auth) {
        Cliente cliente = cliente(auth);
        return polizas.findByClienteIdAndActivaTrue(cliente.id).stream()
                .map(p -> new PolizaResponse(p.numero, p.placa)).toList();
    }
    @PostMapping("/siniestros")
    @ResponseStatus(HttpStatus.CREATED)
    public SiniestroResponse crear(@Valid @RequestBody CrearSiniestroRequest request, Authentication auth) {
        Cliente cliente = cliente(auth);
        Poliza poliza = polizas.findByNumeroAndClienteIdAndActivaTrue(request.numeroPoliza(), cliente.id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Póliza inválida o inactiva"));
        if (!poliza.cubre(request.fechaAccidente().toLocalDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La póliza no cubre la fecha del accidente");
        Siniestro siniestro = new Siniestro();
        siniestro.cliente = cliente;
        siniestro.poliza = poliza;
        siniestro.fechaAccidente = request.fechaAccidente();
        siniestro.ubicacion = request.ubicacion().trim();
        siniestro = siniestros.save(siniestro);
        DetalleSiniestro detalle = new DetalleSiniestro();
        detalle.siniestroId = siniestro.id;
        detalle.descripcion = request.descripcion().trim();
        detalle.fotos = List.copyOf(request.fotos());
        detalle.metadatos = request.metadatos() == null ? Map.of() : Map.copyOf(request.metadatos());
        try {
            detalles.save(detalle);
            siniestro.estado = Siniestro.Estado.REPORTADO;
            siniestros.save(siniestro);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo completar el reporte; conserva los datos e inténtalo más tarde", e);
        }
        return response(siniestro, detalle);
    }
    @GetMapping("/siniestros")
    public List<SiniestroResponse> listar(Authentication auth) {
        return siniestros.findByClienteIdAndEstadoNotOrderByIdDesc(cliente(auth).id, Siniestro.Estado.PENDIENTE_DETALLE)
                .stream().map(s -> response(s, detalles.findBySiniestroId(s.id).orElse(null))).toList();
    }
    @GetMapping("/siniestros/{id}")
    public SiniestroResponse obtener(@PathVariable Long id, Authentication auth) {
        Siniestro siniestro = siniestroPropio(id, auth);
        return response(siniestro, detalles.findBySiniestroId(id).orElse(null));
    }
    @PutMapping("/siniestros/{id}")
    public SiniestroResponse actualizar(@PathVariable Long id,
            @Valid @RequestBody CrearSiniestroRequest request, Authentication auth) {
        Siniestro siniestro = siniestroPropio(id, auth);
        if (siniestro.estado != Siniestro.Estado.REPORTADO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El siniestro ya está en revisión o fue resuelto");
        Poliza poliza = polizas.findByNumeroAndClienteIdAndActivaTrue(request.numeroPoliza(), siniestro.cliente.id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Póliza inválida o inactiva"));
        if (!poliza.cubre(request.fechaAccidente().toLocalDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La póliza no cubre la fecha del accidente");
        DetalleSiniestro detalle = detalles.findBySiniestroId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Falta el detalle del siniestro"));
        detalle.descripcion = request.descripcion().trim();
        detalle.fotos = List.copyOf(request.fotos());
        detalle.metadatos = request.metadatos() == null ? Map.of() : Map.copyOf(request.metadatos());
        detalles.save(detalle);
        siniestro.poliza = poliza;
        siniestro.fechaAccidente = request.fechaAccidente();
        siniestro.ubicacion = request.ubicacion().trim();
        siniestros.save(siniestro);
        return response(siniestro, detalle);
    }
    @DeleteMapping("/siniestros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, Authentication auth) {
        Siniestro siniestro = siniestroPropio(id, auth);
        if (siniestro.estado != Siniestro.Estado.REPORTADO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar un siniestro en revisión o resuelto");
        siniestros.delete(siniestro);
        detalles.findBySiniestroId(id).ifPresent(detalles::delete);
    }
    private Siniestro siniestroPropio(Long id, Authentication auth) {
        return siniestros.findByIdAndClienteId(id, cliente(auth).id)
                .filter(s -> s.estado != Siniestro.Estado.PENDIENTE_DETALLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Siniestro no encontrado"));
    }
    private Cliente cliente(Authentication auth) {
        return clientes.findByEmail(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }
    private SiniestroResponse response(Siniestro s, DetalleSiniestro d) {
        return new SiniestroResponse(s.id, s.poliza.numero, s.poliza.placa,
                s.fechaAccidente, s.ubicacion, s.estado.name(), d == null ? null : d.descripcion,
                d == null ? List.of() : d.fotos, d == null ? Map.of() : d.metadatos,
                s.observacionDecision, s.fechaDecision);
    }
}
