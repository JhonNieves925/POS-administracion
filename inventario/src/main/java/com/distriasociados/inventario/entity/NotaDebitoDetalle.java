package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "nota_debito_detalles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaDebitoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_debito_id", nullable = false)
    @JsonIgnore
    private NotaDebito notaDebito;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    /** Valor total de esta línea (cantidad × precio) */
    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal valorLinea = BigDecimal.ZERO;

    public Long getProductoId() { return producto != null ? producto.getId() : null; }
}
