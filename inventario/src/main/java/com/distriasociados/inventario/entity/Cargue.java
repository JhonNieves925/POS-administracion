package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "cargues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cargue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Número legible del cargue — Ej: CAR-20260325-001
    @Column(nullable = false, unique = true, length = 25)
    private String numero;

    @Column(nullable = false)
    private LocalDate fecha;

    // Vendedor al que se le despacha
    @ManyToOne
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    // Ruta del día (opcional — puede ser diferente a la ruta principal del vendedor)
    @ManyToOne
    @JoinColumn(name = "ruta_id", nullable = true)
    private Ruta ruta;

    // Quién creó el cargue (auxiliar o admin)
    @ManyToOne
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCargue estado = EstadoCargue.CREADO;

    @Column(length = 500)
    private String observaciones;

    // Totales generales (se actualizan al liquidar)
    @Column(nullable = false)
    private Integer totalUnidadesCargue = 0;

    @Column(nullable = false)
    private Integer totalUnidadesDevolucion = 0;

    // Si pertenece a un cargue grupal, todos los del grupo comparten este ID
    // grupoId = id del cargue original (el primero creado en el grupo)
    @Column(name = "grupo_id")
    private Long grupoId;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    // Fecha y hora en que se liquidó
    private LocalDateTime liquidadoEn;

    // Lista de productos del cargue
    // CascadeType.ALL → si borro el cargue, borro sus detalles también
    // orphanRemoval → si quito un detalle de la lista, se borra de la BD
    @JsonManagedReference("cargue-detalles")
    @OneToMany(mappedBy = "cargue", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CargueDetalle> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }

    // Calcula las unidades vendidas (cargue - devolución)
    public Integer getTotalUnidadesVenta() {
        return totalUnidadesCargue - totalUnidadesDevolucion;
    }

    public enum EstadoCargue {
        CREADO,      // Recién creado, aún en bodega
        EN_RUTA,     // Vendedor salió con el producto
        DEVUELTO,    // Vendedor registró sus devoluciones
        LIQUIDADO    // Auxiliar confirmó y cerró el cargue
    }
}