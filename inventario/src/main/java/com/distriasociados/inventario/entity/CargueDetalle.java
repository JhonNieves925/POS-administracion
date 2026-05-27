package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "cargue_detalles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargueDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Referencia al cargue padre
    @JsonBackReference("cargue-detalles")
    @ManyToOne
    @JoinColumn(name = "cargue_id", nullable = false)
    private Cargue cargue;

    // Producto despachado
    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Cuántas cajas/unidades salieron de bodega
    @Column(nullable = false)
    private Integer cantidadCargue = 0;

    // Cuántas regresaron sin vender (lo registra el vendedor desde la PWA)
    @Column(nullable = false)
    private Integer cantidadDevolucion = 0;

    // Precio de la caja al momento del cargue
    // Se guarda aquí porque el precio puede cambiar después
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCajaSnapshot;

    // Vendido = Cargue - Devolución
    public Integer getCantidadVenta() {
        int cargue = cantidadCargue != null ? cantidadCargue : 0;
        int dev = cantidadDevolucion != null ? cantidadDevolucion : 0;
        return cargue - dev;
    }

    // Subtotal de lo vendido = precioUnidad × unidadesVendidas
    // precioUnidad = precioCajaSnapshot ÷ unidadesPorCaja
    public BigDecimal getSubtotalVenta() {
        if (precioCajaSnapshot == null) return BigDecimal.ZERO;
        int upk = (producto != null && producto.getUnidadesPorCaja() > 0)
                  ? producto.getUnidadesPorCaja() : 1;
        BigDecimal precioUnidad = precioCajaSnapshot
            .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);
        return precioUnidad.multiply(BigDecimal.valueOf(getCantidadVenta()))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}