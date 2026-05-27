package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Cliente;
import com.distriasociados.inventario.service.ClienteService;
import com.distriasociados.inventario.service.RemisionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final com.distriasociados.inventario.repository.ClienteRepository clienteRepository;

    // Endpoint especial: verifica si existe CONSUMIDOR FINAL (lo necesita la PWA)
    @GetMapping("/consumidor-final")
    public ResponseEntity<ApiResponse<Cliente>> consumidorFinal() {
        return clienteRepository.findByNit(RemisionService.NIT_CONSUMIDOR_FINAL)
            .map(c -> ResponseEntity.ok(ApiResponse.ok("Consumidor final encontrado", c)))
            .orElse(ResponseEntity.notFound().<ApiResponse<Cliente>>build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Cliente>>> listar(
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(
            ApiResponse.ok("Clientes obtenidos", clienteService.buscar(busqueda))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Cliente>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Cliente encontrado", clienteService.buscarPorId(id))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Cliente>> crear(@Valid @RequestBody Cliente cliente) {
        return ResponseEntity.ok(
            ApiResponse.ok("Cliente creado", clienteService.crear(cliente))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Cliente>> editar(
            @PathVariable Long id, @RequestBody Cliente cliente) {
        return ResponseEntity.ok(
            ApiResponse.ok("Cliente actualizado", clienteService.editar(id, cliente))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        clienteService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Cliente desactivado"));
    }
}