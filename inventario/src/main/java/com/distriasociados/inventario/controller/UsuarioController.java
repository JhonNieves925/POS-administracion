package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.entity.Vendedor;
import com.distriasociados.inventario.repository.VendedorRepository;
import com.distriasociados.inventario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final VendedorRepository vendedorRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Usuario>>> listar() {
        return ResponseEntity.ok(
            ApiResponse.ok("Usuarios obtenidos", usuarioService.listarTodos())
        );
    }

    // Lista vendedores activos para selector de cargues
    @GetMapping("/vendedores")
    public ResponseEntity<ApiResponse<List<Vendedor>>> listarVendedores() {
        return ResponseEntity.ok(
            ApiResponse.ok("Vendedores obtenidos", vendedorRepository.findByActivoTrue())
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Usuario>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Usuario encontrado", usuarioService.buscarPorId(id))
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Usuario>> crear(@RequestBody Usuario usuario) {
        return ResponseEntity.ok(
            ApiResponse.ok("Usuario creado", usuarioService.crear(usuario))
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Usuario>> editar(
            @PathVariable Long id,
            @RequestBody Usuario usuario) {
        return ResponseEntity.ok(
            ApiResponse.ok("Usuario actualizado", usuarioService.editar(id, usuario))
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Usuario desactivado"));
    }

    @PutMapping("/{id}/toggle-activo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Usuario>> toggleActivo(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Estado actualizado", usuarioService.toggleActivo(id))
        );
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetearPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String nuevaPassword = body.get("nuevaPassword");
        usuarioService.resetearPassword(id, nuevaPassword);
        return ResponseEntity.ok(
            ApiResponse.ok("Contrasena reseteada. El usuario debera cambiarla al ingresar.")
        );
    }
}
