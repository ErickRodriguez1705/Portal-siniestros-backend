package com.portalsiniestros.api;

import com.portalsiniestros.model.Cliente;
import com.portalsiniestros.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {
    private final ClienteRepository clientes;
    private final Path uploadRoot;
    public ImagenController(ClienteRepository clientes, @Value("${app.upload-dir}") String uploadDir) {
        this.clientes = clientes;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> subir(@RequestParam("archivo") MultipartFile archivo, Authentication auth) {
        if (archivo.isEmpty() || archivo.getSize() > 5_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen debe pesar entre 1 byte y 5 MB");
        }
        String extension;
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen", e);
        }
        if (esPng(bytes)) extension = ".png";
        else if (esJpeg(bytes)) extension = ".jpg";
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se aceptan imágenes JPG o PNG");
        String fileName = UUID.randomUUID() + extension;
        Path directory = uploadRoot.resolve(cliente(auth).id.toString());
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen", e);
        }
        return Map.of("url", "/api/imagenes/" + fileName);
    }
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> obtener(@PathVariable String fileName, Authentication auth) {
        if (!fileName.matches("[0-9a-f-]{36}\\.(jpg|png)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        Path path = uploadRoot.resolve(cliente(auth).id.toString()).resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }
    private Cliente cliente(Authentication auth) {
        return clientes.findByEmail(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
    }
    private boolean esPng(byte[] bytes) {
        byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }
    private boolean esJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }
}
