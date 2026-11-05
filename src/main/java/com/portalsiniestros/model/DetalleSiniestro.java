package com.portalsiniestros.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document("detalles_siniestro")
public class DetalleSiniestro {
    @Id public String id;
    @Indexed(unique = true) public Long siniestroId;
    public String descripcion;
    public List<String> fotos;
    public Map<String, String> metadatos;
}
