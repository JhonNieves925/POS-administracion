package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;              // Ej: 160046

    @Column(nullable = false, length = 60)
    private String marca;               // Ej: COCA COLA, AGUA BRISA

    @Column(length = 60)
    private String sabor;               // Ej: MANGO, LIMA LIMON (puede ser null)

    @Column(length = 20)
    private String tamanoMl;            // Ej: "600", "1.5", "1/2", "3.5"

    @Column(length = 5)
    private String unidadMedida = "ML"; // ML, OZ, LT

    @Enumerated(EnumType.STRING)
    private Presentacion presentacion;  // PET, VIR, NR, LAT, TETRA

    @Column(nullable = false)
    private Integer unidadesPorCaja;    // Ej: 24, 12, 6, 30

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCaja;      // Precio de venta sugerido por caja

    /**
     * Precio de compra (costo proveedor) por caja.
     * Se usa como PRECIO MÍNIMO de venta — el vendedor no puede vender por debajo.
     * Si es null se ignora la validación de precio mínimo.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal precioCompra;

    /**
     * IBUA — Impuesto a las Bebidas Ultraprocesadas Azucaradas.
     * Valor en COP por UNIDAD (no por caja).
     * Aplica solo a bebidas azucaradas según resolución DIAN.
     * 0 = producto no sujeto al impuesto.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorIbuaUnidad = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoIva tipoIva = TipoIva.EXCLUIDO;

    @Column(length = 50)
    private String siigoCodigo;         // Código del producto en Siigo

    @Column(nullable = false)
    private Integer stockActual = 0;

    @Column(nullable = false)
    private Integer stockMinimo = 0;    // Alerta cuando baje de este número

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

    // Precio de venta sugerido por unidad
    public BigDecimal getPrecioUnidad() {
        if (unidadesPorCaja == null || unidadesPorCaja == 0) return BigDecimal.ZERO;
        return precioCaja.divide(BigDecimal.valueOf(unidadesPorCaja), 2,
                java.math.RoundingMode.HALF_UP);
    }

    // Precio mínimo de venta por unidad (precio costo proveedor ÷ upk)
    // Devuelve null si precioCompra no está configurado
    public BigDecimal getPrecioMinimoUnidad() {
        if (precioCompra == null || unidadesPorCaja == null || unidadesPorCaja == 0) return null;
        return precioCompra.divide(BigDecimal.valueOf(unidadesPorCaja), 2,
                java.math.RoundingMode.HALF_UP);
    }

    // Nombre completo del producto para mostrar
    public String getNombreCompleto() {
        String nombre = marca;
        if (sabor != null && !sabor.isBlank()) nombre += " " + sabor;
        if (tamanoMl != null && !tamanoMl.isBlank()) nombre += " " + tamanoMl;
        if (unidadMedida != null) nombre += unidadMedida;
        if (presentacion != null) nombre += " " + presentacion.name();
        nombre += " (" + unidadesPorCaja + ")";
        return nombre;
        // Resultado: "AGUA BRISA GAS 600ML PET (24)"
    }
    
 // Devuelve el porcentaje de IVA como número
 // Lo necesita Siigo cuando enviamos la factura
 public double getPorcentajeIva() {
     if (tipoIva == null) return 0;
     return switch (tipoIva) {
         case IVA_5  -> 5.0;
         case IVA_19 -> 19.0;
         default     -> 0.0;   // EXCLUIDO y EXENTO = 0
     };
 }

    public enum Presentacion {
        PET, VIR, NR, LAT, TETRA, SA, BIDON
    }

    public enum TipoIva {
        EXCLUIDO, EXENTO, IVA_5, IVA_19
    }
    
}