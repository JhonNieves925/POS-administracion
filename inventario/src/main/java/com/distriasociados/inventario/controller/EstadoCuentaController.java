package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.service.EstadoCuentaService;
import com.distriasociados.inventario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estado-cuenta")
@RequiredArgsConstructor
public class EstadoCuentaController {

    private final EstadoCuentaService estadoCuentaService;
    private final UsuarioService       usuarioService;

    // ── Listar todos los créditos ──────────────────────────────
    // ?soloActivos=true → solo PENDIENTE/PARCIAL (default false)
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listar(
            @RequestParam(defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(
            ApiResponse.ok("Créditos obtenidos",
                estadoCuentaService.listarCreditos(soloActivos))
        );
    }

    // ── Alertas para el dashboard ──────────────────────────────
    // ?dias=3  → vencen en los próximos 3 días (default 3)
    @GetMapping("/alertas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> alertas(
            @RequestParam(defaultValue = "3") int dias) {
        return ResponseEntity.ok(
            ApiResponse.ok("Alertas obtenidas",
                estadoCuentaService.obtenerAlertas(dias))
        );
    }

    // ── Reporte por rango de fechas ────────────────────────────
    @GetMapping("/reporte")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reporte(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Reporte generado",
                estadoCuentaService.generarReporte(inicio, fin))
        );
    }

    // ── Resumen global de cartera ─────────────────────────────
    @GetMapping("/resumen-cartera")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resumenCartera(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Resumen de cartera obtenido",
                estadoCuentaService.resumenCartera(inicio, fin))
        );
    }

    // ── Registrar abono ────────────────────────────────────────
    // Body: { monto, fecha?, notas? }
    @PostMapping("/{remisionId}/abono")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registrarAbono(
            @PathVariable Long remisionId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());

        BigDecimal monto = new BigDecimal(body.getOrDefault("monto", "0").toString());
        String notasStr  = body.getOrDefault("notas", "").toString();
        LocalDate fecha  = null;
        Object fechaRaw  = body.get("fecha");
        if (fechaRaw != null && !fechaRaw.toString().isBlank()) {
            fecha = LocalDate.parse(fechaRaw.toString());
        }

        Map<String, Object> resultado = estadoCuentaService.registrarAbono(
            remisionId, monto, fecha, notasStr.isBlank() ? null : notasStr, usuario);

        return ResponseEntity.ok(ApiResponse.ok("Abono registrado", resultado));
    }

    // ── Marcar como pagada total ───────────────────────────────
    // Body: { notas? }
    @PutMapping("/{remisionId}/pagar-total")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pagarTotal(
            @PathVariable Long remisionId,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth) {

        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        String notas = body != null ? body.getOrDefault("notas", "").toString() : "";

        Map<String, Object> resultado = estadoCuentaService.pagarTotal(
            remisionId, notas.isBlank() ? null : notas, usuario);

        return ResponseEntity.ok(ApiResponse.ok("Remisión marcada como pagada", resultado));
    }
}
