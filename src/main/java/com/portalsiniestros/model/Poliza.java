package com.portalsiniestros.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "polizas")
public class Poliza {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true)
    public String numero;
    @ManyToOne(optional = false)
    public Cliente cliente;
    @Column(nullable = false)
    public String placa;
    @Column(nullable = false)
    public boolean activa;
    public LocalDate inicioVigencia;
    public LocalDate finVigencia;
    public boolean cubre(LocalDate fecha) {
        return activa && (inicioVigencia == null || !fecha.isBefore(inicioVigencia))
                && (finVigencia == null || !fecha.isAfter(finVigencia));
    }
}
