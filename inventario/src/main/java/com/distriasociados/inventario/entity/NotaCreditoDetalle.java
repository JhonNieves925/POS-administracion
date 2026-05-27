package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "nota_credito_detalles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaCreditoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_credito_id", nullable = false)
    @JsonIgnore
    private NotaCredito notaCredito;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** Descripción libre (por si el producto fue eliminado o es cargo sin producto) */
    @Column(nullable = false, length = 200)
    private String descripcion;

    /**
     * Cantidad de unidades a devolver.
     * Solo aplica para TipoNotaCredito.DEVOLUCION / ANULACION.
     * Para AJUSTE_PRECIO se puede dejar en 0.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cantidad = BigDecimal.ZERO;

    /** Precio unitario original (el que estaba en la factura) */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioUnitarioOriginal = BigDecimal.ZERO;

    /**
     * Precio unitario correcto (solo para AJUSTE_PRECIO).
     * La diferencia (original - nuevo) * cantidad = valorLinea.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal precioUnitarioNuevo;

    /** Porcentaje IVA de esta línea (para calcular la nota con impuestos) */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    /** Valor total que se le acredita al cliente en esta línea (sin IVA) */
    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal valorLinea = BigDecimal.ZERO;

    public Long getProductoId() { return producto != null ? producto.getId() : null; }
    public String getProductoNombre() { return producto != null ? producto.getNombreCompleto() : descripcion; }
}
