package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_inventario")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Positivo = entra al inventario / Negativo = sale del inventario
    @Column(nullable = false)
    private Integer cantidad;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    // A qué documento está asociado este movimiento
    // Ej: referenciaId=5, referenciaTipo="CARGUE" → viene del cargue #5
    private Long referenciaId;

    @Column(length = 20)
    private String referenciaTipo;   // CARGUE, COMPRA, REMISION, AJUSTE

    @Column(length = 300)
    private String observaciones;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;         // Quién generó el movimiento

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (fecha == null) fecha = LocalDate.now();
    }

    public enum TipoMovimiento {
        COMPRA,         // Ingreso por compra a proveedor     → stock SUBE
        CARGUE,         // Salida por despacho a vendedor     → stock BAJA
        DEVOLUCION,     // Ingreso por devolución de ruta     → stock SUBE
        VENTA_DIRECTA,  // Salida por venta directa bodega   → stock BAJA
        AJUSTE_POS,     // Ajuste manual positivo             → stock SUBE
        AJUSTE_NEG      // Ajuste manual negativo             → stock BAJA
    }
}