package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "compra_detalles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompraDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "compra_id", nullable = false)
    @JsonIgnore   // corta la referencia circular Compra ↔ CompraDetalle al serializar JSON
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCaja;

    public BigDecimal getSubtotal() {
        return precioCaja.multiply(BigDecimal.valueOf(cantidad));
    }
}