package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "remision_detalles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemisionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference("remision-detalles")
    @ManyToOne
    @JoinColumn(name = "remision_id", nullable = false)
    private Remision remision;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Descripción guardada al momento de la venta
    @Column(nullable = false, length = 200)
    private String descripcionProducto;  // Ej: "COCA COLA ORIGINAL 600ML PET (24)"

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    /**
     * Costo del producto al momento de la venta (precio compra proveedor por unidad).
     * Se guarda para calcular ganancias en reportes sin depender del precio actual.
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    /**
     * IBUA por unidad aplicado en esta línea (COP/unidad).
     * 0 si el producto no está sujeto al impuesto.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorIbuaUnidad = BigDecimal.ZERO;

    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(cantidad)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getValorIva() {
        return getSubtotal()
            .multiply(porcentajeIva)
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    /** IBUA total de esta línea */
    public BigDecimal getValorIbua() {
        if (valorIbuaUnidad == null) return BigDecimal.ZERO;
        return valorIbuaUnidad.multiply(cantidad)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Total línea = subtotal + IVA + IBUA */
    public BigDecimal getTotalLinea() {
        return getSubtotal().add(getValorIva()).add(getValorIbua());
    }

    /** Ganancia bruta de esta línea = (precioVenta - costo) × cantidad */
    public BigDecimal getGananciaLinea() {
        if (costoUnitario == null) return BigDecimal.ZERO;
        return precioUnitario.subtract(costoUnitario)
            .multiply(cantidad)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Costo total de esta línea = costoUnitario × cantidad */
    public BigDecimal getCostoTotal() {
        if (costoUnitario == null) return BigDecimal.ZERO;
        return costoUnitario.multiply(cantidad)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}