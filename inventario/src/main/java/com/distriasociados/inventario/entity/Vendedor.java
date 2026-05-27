package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendedores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 15)
    private String cedula;

    @Column(length = 20)
    private String telefono;

    // Relación con Usuario — cada vendedor tiene su cuenta de acceso
    // Un vendedor = un usuario, un usuario = un vendedor
    @OneToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Ruta principal del vendedor (puede cambiar al crear el cargue)
    // ManyToOne: muchos vendedores pueden tener la misma ruta principal
    @ManyToOne
    @JoinColumn(name = "ruta_principal_id")
    private Ruta rutaPrincipal;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }
}