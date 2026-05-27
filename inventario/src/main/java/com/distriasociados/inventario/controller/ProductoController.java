package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Producto;
import com.distriasociados.inventario.service.ProductoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Producto>>> listar(
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(
            ApiResponse.ok("Productos obtenidos", productoService.buscar(busqueda))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Producto>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Producto encontrado", productoService.buscarPorId(id))
        );
    }

    @GetMapping("/stock-critico")
    public ResponseEntity<ApiResponse<List<Producto>>> stockCritico() {
        return ResponseEntity.ok(
            ApiResponse.ok("Productos stock crítico",
                productoService.stockCritico())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Producto>> crear(
           @Valid @RequestBody Producto producto) {
        return ResponseEntity.ok(
            ApiResponse.ok("Producto creado", productoService.crear(producto))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Producto>> editar(
            @PathVariable Long id,
            @RequestBody Producto producto) {
        return ResponseEntity.ok(
            ApiResponse.ok("Producto actualizado",
                productoService.editar(id, producto))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        productoService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Producto desactivado"));
    }
}