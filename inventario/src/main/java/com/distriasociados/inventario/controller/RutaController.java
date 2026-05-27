package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Ruta;
import com.distriasociados.inventario.service.RutaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rutas")
@RequiredArgsConstructor
public class RutaController {

    private final RutaService rutaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Ruta>>> listar() {
        return ResponseEntity.ok(
            ApiResponse.ok("Rutas obtenidas", rutaService.listarTodas())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ruta>> crear(@RequestBody Ruta ruta) {
        return ResponseEntity.ok(
            ApiResponse.ok("Ruta creada", rutaService.crear(ruta))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Ruta>> editar(
            @PathVariable Long id, @RequestBody Ruta ruta) {
        return ResponseEntity.ok(
            ApiResponse.ok("Ruta actualizada", rutaService.editar(id, ruta))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        rutaService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Ruta desactivada"));
    }
}