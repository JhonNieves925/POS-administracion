package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.ConfiguracionContable;
import com.distriasociados.inventario.service.ConfiguracionContableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
public class ConfiguracionContableController {

    private final ConfiguracionContableService service;

    /** Listar todas las cuentas PUC configuradas */
    @GetMapping("/cuentas")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<ApiResponse<List<ConfiguracionContable>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok("Cuentas PUC", service.listar()));
    }

    /**
     * Actualizar cuentas en bulk.
     * Body: [ { "clave": "VENTA_CONTADO", "cuentaPuc": "4135", "nombreCuenta": "..." }, ... ]
     */
    @PutMapping("/cuentas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ConfiguracionContable>>> actualizar(
            @RequestBody List<Map<String, String>> cambios) {
        List<ConfiguracionContable> resultado = service.actualizarBulk(cambios);
        return ResponseEntity.ok(ApiResponse.ok("Cuentas PUC actualizadas", resultado));
    }
}
