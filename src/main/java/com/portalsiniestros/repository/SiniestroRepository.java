package com.portalsiniestros.repository;

import com.portalsiniestros.model.Siniestro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SiniestroRepository extends JpaRepository<Siniestro, Long> {
    List<Siniestro> findByClienteIdAndEstadoNotOrderByIdDesc(Long clienteId, Siniestro.Estado estado);
    Optional<Siniestro> findByIdAndClienteId(Long id, Long clienteId);
}
