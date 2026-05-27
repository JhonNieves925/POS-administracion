package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "factura_detalles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "factura_id", nullable = false)
    @JsonIgnore  // Evita referencia circular: Factura → detalles → FacturaDetalle.factura → Factura
    private Factura factura;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(cantidad).subtract(descuento);
    }

    public BigDecimal getValorIva() {
        return getSubtotal()
            .multiply(porcentajeIva)
            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalLinea() {
        return getSubtotal().add(getValorIva());
    }
}