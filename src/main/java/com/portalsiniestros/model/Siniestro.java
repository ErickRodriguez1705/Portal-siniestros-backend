package com.portalsiniestros.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.Instant;

@Entity
@Table(name = "siniestros")
public class Siniestro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public Cliente cliente;
    @ManyToOne(optional = false)
    public Poliza poliza;
    @Column(nullable = false)
    public LocalDateTime fechaAccidente;
    @Column(nullable = false)
    public String ubicacion;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public Estado estado = Estado.PENDIENTE_DETALLE;
    @Column(length = 1000)
    public String observacionDecision;
    public Instant fechaDecision;
    public enum Estado { PENDIENTE_DETALLE, REPORTADO, EN_REVISION, APROBADO, RECHAZADO }
}
