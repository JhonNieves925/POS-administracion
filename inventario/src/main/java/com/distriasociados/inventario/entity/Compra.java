package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "compras")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String proveedor;               // Nombre del proveedor

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 50)
    private String numeroFacturaProveedor;  // Número de factura que trae el proveedor

    /**
     * CUFE de la factura electrónica (hash SHA-384, 96 caracteres hex).
     * Se usa para detectar duplicados: no se puede registrar la misma factura dos veces.
     * Null cuando la compra se registró manualmente sin CUFE.
     */
    @Column(length = 96, unique = true)
    private String cufe;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500)
    private String observaciones;

    @ManyToOne
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CompraDetalle> detalles = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (fecha == null) fecha = LocalDate.now();
    }
}