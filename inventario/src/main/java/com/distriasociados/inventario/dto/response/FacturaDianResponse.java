package com.distriasociados.inventario.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos extraídos de una factura electrónica de la DIAN.
 *
 * Fuentes posibles:
 *  - "CUFE"    → solo validación de formato + historial nuestro (sin consulta DIAN)
 *  - "XML"     → archivo UBL 2.1 parseado completamente (incluye líneas de producto)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaDianResponse {

    // ── Identificación ────────────────────────────────────────────
    private String    cufe;
    private String    fuente;          // "CUFE" | "XML"
    private boolean   duplicado;       // true si el CUFE ya fue registrado antes

    // ── Encabezado de la factura ──────────────────────────────────
    private String    proveedor;
    private String    proveedorNit;
    private String    numeroFactura;
    private LocalDate fecha;
    private BigDecimal total;
    private BigDecimal subtotal;
    private BigDecimal totalImpuestos;

    // ── Líneas del XML (solo cuando fuente = "XML") ───────────────
    @Builder.Default
    private List<LineaFactura> lineas = new ArrayList<>();

    // ── Historial de productos comprados al mismo proveedor ────────
    @Builder.Default
    private List<ProductoSugerido> sugerencias = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineaFactura {
        /** Descripción tal como viene en la factura del proveedor */
        private String     descripcionDian;
        private BigDecimal cantidad;
        private BigDecimal precioUnitario;  // Precio por unidad/caja según la factura
        private BigDecimal subtotal;
        private BigDecimal porcentajeIva;
        /** Si encontramos un producto que coincide en nuestro catálogo, lo pre-seleccionamos */
        private Long       productoIdSugerido;
        private String     productoNombreSugerido;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductoSugerido {
        private Long       productoId;
        private String     nombreProducto;
        private BigDecimal ultimoPrecioCaja;
        private Integer    ultimaCantidad;
        private LocalDate  ultimaCompra;
    }
}
