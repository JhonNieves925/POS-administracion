package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.request.CrearFacturaManualRequest;
import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Factura;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.service.AuditService;
import com.distriasociados.inventario.service.FacturaService;
import com.distriasociados.inventario.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
public class FacturaController {

    private final FacturaService facturaService;
    private final UsuarioService usuarioService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Factura>>> listar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Facturas obtenidas", facturaService.listarPorFecha(inicio, fin))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Factura>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Factura encontrada", facturaService.buscarPorId(id))
        );
    }

    @GetMapping("/por-numero/{numero}")
    public ResponseEntity<ApiResponse<Factura>> buscarPorNumero(@PathVariable String numero) {
        return ResponseEntity.ok(
            ApiResponse.ok("Factura encontrada", facturaService.buscarPorNumero(numero))
        );
    }

    @PostMapping("/desde-remision/{remisionId}")
    public ResponseEntity<ApiResponse<Factura>> crearDesdeRemision(
            @PathVariable Long remisionId,
            Authentication auth,
            HttpServletRequest httpRequest) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Factura factura = facturaService.crearDesdeRemision(remisionId, usuario);
        auditService.registrar(auth.getName(), "CREAR_FACTURA", "FACTURA", factura.getId(),
            "Desde remisión " + remisionId + " → " + factura.getNumero(), obtenerIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Factura creada", factura));
    }

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<Factura>> crearManual(
            @Valid @RequestBody CrearFacturaManualRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Factura factura = facturaService.crearManualDesdeRequest(request, usuario);
        auditService.registrar(auth.getName(), "CREAR_FACTURA", "FACTURA", factura.getId(),
            factura.getNumero(), obtenerIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Factura creada", factura));
    }

    @PostMapping("/{id}/emitir")
    public ResponseEntity<ApiResponse<Factura>> emitir(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest httpRequest) {
        Factura factura = facturaService.emitirSiigo(id);
        auditService.registrar(auth.getName(), "EMITIR_DIAN", "FACTURA", id,
            factura.getNumero() + " | CUFE: " + factura.getCufe(), obtenerIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Factura emitida en DIAN", factura));
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Factura>> anular(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest httpRequest) {
        Factura factura = facturaService.anular(id);
        auditService.registrar(auth.getName(), "ANULAR_FACTURA", "FACTURA", id,
            factura.getNumero(), obtenerIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Factura anulada", factura));
    }

    private String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
