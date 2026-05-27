package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.facturacion.ResumenFacturacionDto;
import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.service.FacturacionElectronicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facturacion")
@RequiredArgsConstructor
public class FacturacionController {

    private final FacturacionElectronicaService facturacionService;

    /**
     * GET /api/facturacion/resumen?fecha=2025-01-15
     * Previsualización: qué facturas se van a crear sin enviar nada.
     */
    @GetMapping("/resumen")
    public ResponseEntity<ApiResponse<ResumenFacturacionDto>> resumen(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        if (fecha == null) fecha = LocalDate.now();
        return ResponseEntity.ok(
            ApiResponse.ok("Resumen de facturación", facturacionService.resumenDia(fecha))
        );
    }

    /**
     * POST /api/facturacion/facturar-dia?fecha=2025-01-15
     * Envía todas las remisiones PENDIENTE del día a Siigo.
     */
    @PostMapping("/facturar-dia")
    public ResponseEntity<ApiResponse<Map<String, Object>>> facturarDia(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        if (fecha == null) fecha = LocalDate.now();
        Map<String, Object> resultado = facturacionService.facturarDia(fecha);
        return ResponseEntity.ok(ApiResponse.ok("Facturación procesada", resultado));
    }

    /**
     * POST /api/facturacion/reintentar/{remisionId}
     * Reintenta enviar una remisión específica que tuvo error.
     */
    @PostMapping("/reintentar/{remisionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reintentar(
            @PathVariable Long remisionId) {

        Map<String, Object> resultado = facturacionService.reintentarRemision(remisionId);
        return ResponseEntity.ok(ApiResponse.ok("Reintento procesado", resultado));
    }

    /**
     * GET /api/facturacion/pendientes-dia?fecha=2025-01-15
     * Devuelve la lista COMPLETA de remisiones PENDIENTE del día con todo el detalle
     * de líneas, precios e IVA — para que el admin verifique antes de enviar.
     */
    @GetMapping("/pendientes-dia")
    public ResponseEntity<ApiResponse<List<Remision>>> pendientesDia(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        if (fecha == null) fecha = LocalDate.now();
        return ResponseEntity.ok(
            ApiResponse.ok("Remisiones pendientes del día",
                facturacionService.pendientesDia(fecha))
        );
    }

    /**
     * GET /api/facturacion/historial
     * Lista todas las remisiones que ya fueron procesadas (facturadas o con error).
     */
    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<List<Remision>>> historial(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {

        return ResponseEntity.ok(
            ApiResponse.ok("Historial de facturación", facturacionService.historial(inicio, fin))
        );
    }

    /**
     * POST /api/facturacion/sincronizar-facturas
     * Crea los registros Factura faltantes para remisiones históricas facturadas.
     * Idempotente — puede llamarse varias veces sin duplicar datos.
     */
    @PostMapping("/sincronizar-facturas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sincronizarFacturas() {
        return ResponseEntity.ok(
            ApiResponse.ok("Sincronización de facturas",
                facturacionService.sincronizarFacturasExistentes())
        );
    }

    /**
     * GET /api/facturacion/{remisionId}/pdf
     * Descarga el PDF de la factura electrónica en formato Siigo/DIAN.
     */
    @GetMapping("/{remisionId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long remisionId) {
        byte[] pdfBytes = facturacionService.generarPdfFactura(remisionId);
        String nombre = facturacionService.nombrePdfFactura(remisionId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }
}
