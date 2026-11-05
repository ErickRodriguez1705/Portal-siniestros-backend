package com.portalsiniestros.repository;

import com.portalsiniestros.model.DetalleSiniestro;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface DetalleRepository extends MongoRepository<DetalleSiniestro, String> {
    Optional<DetalleSiniestro> findBySiniestroId(Long siniestroId);
}
