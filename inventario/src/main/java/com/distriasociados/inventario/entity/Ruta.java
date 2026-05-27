package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "rutas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String codigo;       // Ej: R1, R2, CENTRO

    @Column(nullable = false, length = 80)
    private String nombre;       // Ej: Ruta Norte, Centro, Municipios

    @Column(length = 200)
    private String descripcion;  // Zonas o municipios que cubre

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    // Una ruta puede tener varios vendedores asignados
    // mappedBy indica que Vendedor es quien tiene la llave foránea
    @OneToMany(mappedBy = "rutaPrincipal", fetch = FetchType.LAZY)
    private List<Vendedor> vendedores;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }
}