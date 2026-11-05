package com.portalsiniestros.api;

import com.portalsiniestros.model.DetalleSiniestro;
import com.portalsiniestros.model.Siniestro;
import com.portalsiniestros.repository.DetalleRepository;
import com.portalsiniestros.repository.SiniestroRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/siniestros")
public class AdminSiniestroController {
    private final SiniestroRepository siniestros;
    private final DetalleRepository detalles;
    private final Path uploadRoot;

    public AdminSiniestroController(SiniestroRepository siniestros, DetalleRepository detalles,
                                    @Value("${app.upload-dir}") String uploadDir) {
        this.siniestros = siniestros;
        this.detalles = detalles;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public record Resumen(Long id, String clienteNombre, String clienteEmail, String numeroPoliza,
                          String placa, LocalDateTime fechaAccidente, String ubicacion, String estado) {}
    public record Detalle(Long id, String clienteNombre, String clienteEmail, String numeroPoliza,
                          String placa, LocalDateTime fechaAccidente, String ubicacion, String estado,
                          String descripcion, List<String> fotos, Map<String, String> metadatos,
                          String observacionDecision, Instant fechaDecision) {}
    public record CambiarEstadoRequest(@NotNull Siniestro.Estado estado, @Size(max = 1000) String motivo) {}

    @GetMapping
    public List<Resumen> listar() {
        return siniestros.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(s -> s.estado != Siniestro.Estado.PENDIENTE_DETALLE)
                .map(this::resumen).toList();
    }

    @GetMapping("/{id}")
    public Detalle obtener(@PathVariable Long id) {
        Siniestro siniestro = buscar(id);
        DetalleSiniestro detalle = detalles.findBySiniestroId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle no encontrado"));
        return new Detalle(siniestro.id, siniestro.cliente.nombre, siniestro.cliente.email,
                siniestro.poliza.numero, siniestro.poliza.placa, siniestro.fechaAccidente,
                siniestro.ubicacion, siniestro.estado.name(), detalle.descripcion,
                detalle.fotos == null ? List.of() : detalle.fotos,
                detalle.metadatos == null ? Map.of() : detalle.metadatos,
                siniestro.observacionDecision, siniestro.fechaDecision);
    }

    @PutMapping("/{id}/estado")
    public Detalle cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        Siniestro siniestro = buscar(id);
        if (siniestro.estado == Siniestro.Estado.APROBADO || siniestro.estado == Siniestro.Estado.RECHAZADO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El siniestro ya tiene una decisión final");
        Siniestro.Estado nuevo = request.estado();
        if (nuevo != Siniestro.Estado.EN_REVISION && nuevo != Siniestro.Estado.APROBADO
                && nuevo != Siniestro.Estado.RECHAZADO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no permitido");
        if (nuevo == Siniestro.Estado.EN_REVISION && siniestro.estado != Siniestro.Estado.REPORTADO)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El siniestro ya está en revisión");
        String motivo = request.motivo() == null ? "" : request.motivo().trim();
        if (nuevo == Siniestro.Estado.RECHAZADO && motivo.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el motivo del rechazo");
        siniestro.estado = nuevo;
        siniestro.observacionDecision = nuevo == Siniestro.Estado.EN_REVISION || motivo.isBlank() ? null : motivo;
        siniestro.fechaDecision = nuevo == Siniestro.Estado.EN_REVISION ? null : Instant.now();
        siniestros.save(siniestro);
        return obtener(id);
    }

    @GetMapping("/{id}/imagenes/{fileName:.+}")
    public ResponseEntity<Resource> imagen(@PathVariable Long id, @PathVariable String fileName) {
        if (!fileName.matches("[0-9a-f-]{36}\\.(jpg|png)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        Siniestro siniestro = buscar(id);
        DetalleSiniestro detalle = detalles.findBySiniestroId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle no encontrado"));
        if (detalle.fotos == null || !detalle.fotos.contains("/api/imagenes/" + fileName))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no asociada al siniestro");
        Path path = uploadRoot.resolve(siniestro.cliente.id.toString()).resolve(fileName);
        if (!Files.isRegularFile(path))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }

    private Siniestro buscar(Long id) {
        return siniestros.findById(id)
                .filter(s -> s.estado != Siniestro.Estado.PENDIENTE_DETALLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Siniestro no encontrado"));
    }
    private Resumen resumen(Siniestro s) {
        return new Resumen(s.id, s.cliente.nombre, s.cliente.email, s.poliza.numero,
                s.poliza.placa, s.fechaAccidente, s.ubicacion, s.estado.name());
    }
}
