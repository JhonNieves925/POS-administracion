package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.repository.RemisionRepository;
import com.distriasociados.inventario.repository.VendedorRepository;
import com.distriasociados.inventario.service.CargueService;
import com.distriasociados.inventario.service.PdfService;
import com.distriasociados.inventario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cargues")
@RequiredArgsConstructor
public class CargueController {

    private final CargueService cargueService;
    private final UsuarioService usuarioService;
    private final VendedorRepository vendedorRepository;
    private final PdfService pdfService;
    private final RemisionRepository remisionRepository;

    // Cargues del dia - para el dashboard
    @GetMapping("/hoy")
    public ResponseEntity<ApiResponse<List<Cargue>>> carguesHoy() {
        return ResponseEntity.ok(
            ApiResponse.ok("Cargues del dia", cargueService.carguesDelDia())
        );
    }

    // Cargue activo del vendedor autenticado hoy - para PWA movil
    @GetMapping("/mi-cargue-hoy")
    public ResponseEntity<ApiResponse<Cargue>> miCargueHoy(Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());

        // Buscar el Vendedor asociado al usuario autenticado
        Vendedor vendedor = vendedorRepository.findByUsuarioId(usuario.getId())
            .orElse(null);

        if (vendedor == null) {
            return ResponseEntity.notFound().build();
        }

        Cargue cargue = cargueService.cargueActivoVendedor(vendedor.getId());
        if (cargue == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok("Tu cargue de hoy", cargue));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Cargue>>> listar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Cargues obtenidos", cargueService.listarPorFecha(inicio, fin))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Cargue>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Cargue encontrado", cargueService.buscarPorId(id))
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Cargue>> crear(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Cargue cargue = cargueService.crearDesdeRequest(body, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Cargue creado", cargue));
    }

    @PutMapping("/{id}/en-ruta")
    @PatchMapping("/{id}/en-ruta")
    public ResponseEntity<ApiResponse<Cargue>> marcarEnRuta(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Vendedor en ruta", cargueService.marcarEnRuta(id))
        );
    }

    @PutMapping("/{id}/devoluciones")
    @PatchMapping("/{id}/devoluciones")
    public ResponseEntity<ApiResponse<Cargue>> registrarDevoluciones(
            @PathVariable Long id,
            @RequestBody List<Map<String, Object>> devoluciones) {
        return ResponseEntity.ok(
            ApiResponse.ok("Devoluciones registradas",
                cargueService.registrarDevolucionesDesdeRequest(id, devoluciones))
        );
    }

    @PutMapping("/{id}/liquidar")
    @PatchMapping("/{id}/liquidar")
    public ResponseEntity<ApiResponse<Cargue>> liquidar(
            @PathVariable Long id,
            Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        return ResponseEntity.ok(
            ApiResponse.ok("Cargue liquidado", cargueService.liquidar(id, usuario))
        );
    }

    // Duplicar cargue para uno o más vendedores adicionales
    @PostMapping("/{id}/duplicar")
    public ResponseEntity<ApiResponse<List<Cargue>>> duplicar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        @SuppressWarnings("unchecked")
        List<Integer> raw = (List<Integer>) body.get("vendedorIds");
        List<Long> vendedorIds = raw.stream().map(Long::valueOf).collect(java.util.stream.Collectors.toList());
        List<Cargue> creados = cargueService.duplicar(id, vendedorIds, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Cargues duplicados: " + creados.size(), creados));
    }

    // Mapa de unidades vendidas a nivel de grupo (para sincronización en PWA)
    @GetMapping("/{id}/grupo-vendido")
    public ResponseEntity<ApiResponse<Map<Long, Integer>>> grupoVendido(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Vendido por grupo", cargueService.grupoVendidoMap(id)));
    }

    // Eliminar un cargue y sus remisiones
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> eliminar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Cargue eliminado", cargueService.eliminarCargue(id))
        );
    }

    // Eliminar todos los cargues
    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> eliminarTodos() {
        return ResponseEntity.ok(
            ApiResponse.ok("Historial limpiado", cargueService.eliminarTodosCargues())
        );
    }

    // PDF de remision — descarga
    @GetMapping("/{id}/remision-pdf")
    public ResponseEntity<byte[]> remisionPdf(@PathVariable Long id) {
        Cargue cargue = cargueService.buscarPorId(id);
        List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(id);
        byte[] pdf = pdfService.generarRemisionCargue(cargue, remisiones);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"remision_" + cargue.getNumero() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    // PDF de remision — visualización en nueva pestaña (inline)
    @GetMapping("/{id}/remision-pdf/ver")
    public ResponseEntity<byte[]> remisionPdfVer(@PathVariable Long id) {
        Cargue cargue = cargueService.buscarPorId(id);
        List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(id);
        byte[] pdf = pdfService.generarRemisionCargue(cargue, remisiones);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"remision_" + cargue.getNumero() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
