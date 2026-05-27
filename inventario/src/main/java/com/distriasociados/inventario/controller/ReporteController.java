package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.service.ExcelService;
import com.distriasociados.inventario.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AUXILIAR')")
public class ReporteController {

    private final ReporteService reporteService;
    private final ExcelService excelService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ventas")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reporteVentas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long vendedorId) {

        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        return ResponseEntity.ok(
            ApiResponse.ok("Reporte de ventas",
                reporteService.reporteVentas(desde, hasta, vendedorId))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inventario")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reporteInventario() {
        return ResponseEntity.ok(
            ApiResponse.ok("Reporte de inventario", reporteService.reporteInventario())
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/devoluciones")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reporteDevoluciones(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long vendedorId) {

        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        return ResponseEntity.ok(
            ApiResponse.ok("Reporte de devoluciones",
                reporteService.reporteDevoluciones(desde, hasta, vendedorId))
        );
    }

    /**
     * GET /api/reportes/ganancias?desde=&hasta=&vendedorId=&detalle=true|false
     * Retorna: ingresoTotal, costoTotal, gananciaTotal, margen%, IVA, IBUA
     * Con detalle=true incluye desglose por producto ordenado por ganancia.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ganancias")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reporteGanancias(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(defaultValue = "false") boolean detalle) {

        return ResponseEntity.ok(
            ApiResponse.ok("Reporte de ganancias",
                reporteService.reporteGanancias(desde, hasta, vendedorId, detalle))
        );
    }

    /**
     * GET /api/reportes/kardex?desde=&hasta=
     * Kardex: stock inicial, entradas (compras), ventas netas, stock final — en cajas.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/kardex")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reporteKardex(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(
            ApiResponse.ok("Kardex de movimientos",
                reporteService.reporteKardex(desde, hasta))
        );
    }

    /**
     * GET /api/reportes/relacion-ventas?desde=&hasta=
     * Relación de ventas agrupada por cuenta PUC para el contador.
     * Incluye notas crédito y débito del período.
     */
    @GetMapping("/relacion-ventas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> relacionVentas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(
            ApiResponse.ok("Relación de ventas contable",
                reporteService.relacionVentas(desde, hasta))
        );
    }

    /**
     * GET /api/reportes/nulas?desde=&hasta=
     * Remisiones anuladas en el período: conteo, valor total y desglose por producto.
     */
    @GetMapping("/nulas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reporteNulas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(
            ApiResponse.ok("Reporte de remisiones anuladas",
                reporteService.reporteNulas(desde, hasta))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ventas/excel")
    public ResponseEntity<byte[]> exportarVentasExcel(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long vendedorId) {

        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        List<Map<String, Object>> datos = reporteService.reporteVentas(desde, hasta, vendedorId);
        byte[] excel = excelService.generarReporteVentas(datos);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte_ventas.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel);
    }
}
