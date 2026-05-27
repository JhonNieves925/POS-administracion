package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.NotaCredito;
import com.distriasociados.inventario.entity.NotaCredito.TipoNotaCredito;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.service.NotaCreditoService;
import com.distriasociados.inventario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notas-credito")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
public class NotaCreditoController {

    private final NotaCreditoService service;
    private final UsuarioService     usuarioService;

    /** Historial global de notas crédito (con filtro de fecha opcional) */
    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<List<NotaCredito>>> listarTodas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin   == null) fin   = LocalDate.now();
        return ResponseEntity.ok(
            ApiResponse.ok("Notas crédito", service.listarTodas(inicio, fin)));
    }

    /** Listar notas crédito de una factura específica */
    @GetMapping("/factura/{facturaId}")
    public ResponseEntity<ApiResponse<List<NotaCredito>>> listarPorFactura(
            @PathVariable Long facturaId) {
        return ResponseEntity.ok(
            ApiResponse.ok("Notas crédito", service.listarPorFactura(facturaId)));
    }

    /**
     * Crear nota crédito.
     * Body: {
     *   "tipo": "DEVOLUCION|AJUSTE_PRECIO|ANULACION",
     *   "motivo": "Descripción",
     *   "lineas": [
     *     {
     *       "productoId": 5,
     *       "descripcion": "Coca-Cola 350ml PET",
     *       "cantidad": 10,
     *       "precioUnitarioOriginal": 2500.00,
     *       "precioUnitarioNuevo": 2000.00,   <- solo para AJUSTE_PRECIO
     *       "porcentajeIva": 0
     *     }
     *   ]
     * }
     */
    @PostMapping("/factura/{facturaId}")
    public ResponseEntity<ApiResponse<NotaCredito>> crear(
            @PathVariable Long facturaId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        Usuario usuario = usuarioService.buscarPorCedula(userDetails.getUsername());
        TipoNotaCredito tipo = TipoNotaCredito.valueOf(body.get("tipo").toString());
        String motivo = body.getOrDefault("motivo", "").toString();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineas = (List<Map<String, Object>>) body.get("lineas");

        NotaCredito nota = service.crear(facturaId, tipo, motivo, lineas, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Nota crédito creada: " + nota.getNumero(), nota));
    }
}
