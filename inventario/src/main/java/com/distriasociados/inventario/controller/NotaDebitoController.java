package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.NotaDebito;
import com.distriasociados.inventario.entity.NotaDebito.TipoNotaDebito;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.service.NotaDebitoService;
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
@RequestMapping("/api/notas-debito")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
public class NotaDebitoController {

    private final NotaDebitoService service;
    private final UsuarioService    usuarioService;

    /** Historial global de notas débito (con filtro de fecha opcional) */
    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<List<NotaDebito>>> listarTodas(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin   == null) fin   = LocalDate.now();
        return ResponseEntity.ok(
            ApiResponse.ok("Notas débito", service.listarTodas(inicio, fin)));
    }

    /** Listar notas débito de una factura específica */
    @GetMapping("/factura/{facturaId}")
    public ResponseEntity<ApiResponse<List<NotaDebito>>> listarPorFactura(
            @PathVariable Long facturaId) {
        return ResponseEntity.ok(
            ApiResponse.ok("Notas débito", service.listarPorFactura(facturaId)));
    }

    /**
     * Crear nota débito.
     * Body: {
     *   "tipo": "INTERESES|AJUSTE_PRECIO|CARGO_ADICIONAL",
     *   "motivo": "Descripción",
     *   "lineas": [
     *     {
     *       "descripcion": "Intereses por mora 30 días",
     *       "cantidad": 1,
     *       "precioUnitario": 15000.00,
     *       "porcentajeIva": 0
     *     }
     *   ]
     * }
     */
    @PostMapping("/factura/{facturaId}")
    public ResponseEntity<ApiResponse<NotaDebito>> crear(
            @PathVariable Long facturaId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        Usuario usuario = usuarioService.buscarPorCedula(userDetails.getUsername());
        TipoNotaDebito tipo = TipoNotaDebito.valueOf(body.get("tipo").toString());
        String motivo = body.getOrDefault("motivo", "").toString();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineas = (List<Map<String, Object>>) body.get("lineas");

        NotaDebito nota = service.crear(facturaId, tipo, motivo, lineas, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Nota débito creada: " + nota.getNumero(), nota));
    }
}
