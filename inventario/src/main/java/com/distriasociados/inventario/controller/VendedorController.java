package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Vendedor;
import com.distriasociados.inventario.service.VendedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/vendedores")
@RequiredArgsConstructor
public class VendedorController {

    private final VendedorService vendedorService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Vendedor>>> listar() {
        return ResponseEntity.ok(
            ApiResponse.ok("Vendedores obtenidos", vendedorService.listarTodos())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendedor>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Vendedor encontrado", vendedorService.buscarPorId(id))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Vendedor>> crear(
            @RequestBody Vendedor vendedor) {
        return ResponseEntity.ok(
            ApiResponse.ok("Vendedor creado", vendedorService.crear(vendedor))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendedor>> editar(
            @PathVariable Long id, @RequestBody Vendedor vendedor) {
        return ResponseEntity.ok(
            ApiResponse.ok("Vendedor actualizado",
                vendedorService.editar(id, vendedor))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        vendedorService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Vendedor desactivado"));
    }
}