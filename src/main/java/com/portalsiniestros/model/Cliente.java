package com.portalsiniestros.model;

import jakarta.persistence.*;

@Entity
@Table(name = "clientes")
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public String nombre;
    @Column(nullable = false, unique = true)
    public String email;
    @Column(nullable = false)
    public String passwordHash;
    @Column(length = 20)
    public String rol = "CLIENTE";
    public String rolEfectivo() { return "ADMIN".equals(rol) ? "ADMIN" : "CLIENTE"; }
}
