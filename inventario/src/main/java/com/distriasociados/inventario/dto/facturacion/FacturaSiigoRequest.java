package com.distriasociados.inventario.dto.facturacion;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos necesarios para crear una factura en Siigo.
 * Se construye a partir de una o varias remisiones agrupadas.
 */
@Data
@Builder
public class FacturaSiigoRequest {

    // ── Datos del cliente ──────────────────────────────────────
    private String clienteNit;           // NIT o cédula del comprador
    private String clienteNombre;        // Razón social o nombre
    private String clienteTipoPersona;   // "NATURAL" o "JURIDICA"

    // ── Datos de la factura ────────────────────────────────────
    private LocalDate fecha;
    private LocalDate fechaVencimiento; // due_date en Siigo; igual a fecha si es CONTADO
    private String formaPago;           // "CONTADO" o "CREDITO"
    private List<LineaFacturaDto> lineas;
    private BigDecimal totalSinIva;
    private BigDecimal totalIva;
    private BigDecimal total;

    // ── Trazabilidad interna ───────────────────────────────────
    /** IDs de las remisiones que componen esta factura */
    private List<Long> remisionIds;

    /** true = factura consolidada de consumidor final del día */
    private boolean esConsolidadaConsumidorFinal;
}
