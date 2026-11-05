package com.portalsiniestros.repository;

import com.portalsiniestros.model.Poliza;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PolizaRepository extends JpaRepository<Poliza, Long> {
    List<Poliza> findByClienteIdAndActivaTrue(Long clienteId);
    Optional<Poliza> findByNumeroAndClienteIdAndActivaTrue(String numero, Long clienteId);
    Optional<Poliza> findByNumero(String numero);
}
