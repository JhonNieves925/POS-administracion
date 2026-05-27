package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.entity.Vendedor;
import com.distriasociados.inventario.repository.CargueRepository;
import com.distriasociados.inventario.repository.VendedorRepository;
import com.distriasociados.inventario.repository.RemisionRepository;
import com.distriasociados.inventario.service.PdfService;
import com.distriasociados.inventario.service.RemisionService;
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
@RequestMapping("/api/remisiones")
@RequiredArgsConstructor
public class RemisionController {

    private final RemisionService remisionService;
    private final UsuarioService usuarioService;
    private final PdfService pdfService;
    private final VendedorRepository vendedorRepository;
    private final RemisionRepository remisionRepository;
    private final CargueRepository cargueRepositoryLocal;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Remision>>> listar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Remisiones obtenidas",
                remisionService.listarPorFecha(inicio, fin))
        );
    }

    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<Remision>>> pendientes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(
            ApiResponse.ok("Remisiones pendientes",
                remisionService.pendientesPorFecha(fecha))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Remision>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.ok("Remision encontrada", remisionService.buscarPorId(id))
        );
    }

    // Remisiones del cargue activo del vendedor hoy (para la PWA — historial de ventas)
    // Filtra por CARGUE (no por fecha global) → cada cargue tiene su propio historial limpio
    @GetMapping("/mis-remisiones")
    public ResponseEntity<ApiResponse<List<Remision>>> misRemisiones(Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Vendedor vendedor = vendedorRepository.findByUsuarioId(usuario.getId())
            .orElse(null);
        if (vendedor == null) {
            return ResponseEntity.ok(ApiResponse.ok("Sin remisiones", List.of()));
        }
        // Tomar el cargue más reciente del vendedor hoy (activo o ya liquidado)
        List<com.distriasociados.inventario.entity.Cargue> carguesHoy =
            cargueRepositoryLocal.findByFecha(LocalDate.now()).stream()
                .filter(c -> c.getVendedor().getId().equals(vendedor.getId()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .toList();
        if (carguesHoy.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok("Sin remisiones", List.of()));
        }
        Long cargueId = carguesHoy.get(0).getId();
        List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(cargueId);
        return ResponseEntity.ok(ApiResponse.ok("Remisiones obtenidas", remisiones));
    }

    // Crea la remision desde el payload del vendedor en la PWA
    // Body: { cargueId, clienteId, observaciones, detalles:[{cargueDetalleId, cantidadCajas, cantidadUnidades}] }
    @PostMapping
    public ResponseEntity<ApiResponse<Remision>> crear(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Remision remision = remisionService.crearDesdeRequest(body, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Remision creada", remision));
    }

    // PDF de la remision de venta
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> remisionPdf(@PathVariable Long id) {
        Remision remision = remisionService.buscarPorId(id);
        byte[] pdf = pdfService.generarRemisionVenta(remision);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"remision_" + remision.getNumero() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    // Vista previa PDF factura electrónica DIAN con marca de agua (sin valor comercial real)
    // Se abre inline en el navegador para que el admin verifique antes de enviar a la DIAN
    @GetMapping("/{id}/preview-dian")
    public ResponseEntity<byte[]> vistaPreviaDian(@PathVariable Long id) {
        Remision remision = remisionService.buscarPorId(id);
        byte[] pdf = pdfService.generarVistaPreviaFacturaDian(remision);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"preview_dian_" + remision.getNumero() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    // Remisiones de un cargue específico (para admin DetalleCargueView)
    @GetMapping("/cargue/{cargueId}")
    public ResponseEntity<ApiResponse<List<Remision>>> porCargue(@PathVariable Long cargueId) {
        List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(cargueId);
        return ResponseEntity.ok(ApiResponse.ok("Remisiones del cargue", remisiones));
    }

    // Editar remisión (cambiar cantidades/precios sin anular)
    @PutMapping("/{id}/actualizar")
    public ResponseEntity<ApiResponse<Remision>> actualizar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Remision actualizada = remisionService.actualizar(id, body);
        return ResponseEntity.ok(ApiResponse.ok("Remisión actualizada", actualizada));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Map<String, Object>>> anular(@PathVariable Long id) {
        Remision anulada = remisionService.anular(id);
        // Devolver solo los campos básicos — evita serializar el grafo completo
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("id",     anulada.getId());
        resp.put("numero", anulada.getNumero());
        resp.put("estado", anulada.getEstado().name());
        resp.put("total",  anulada.getTotal());
        return ResponseEntity.ok(ApiResponse.ok("Remision anulada", resp));
    }
}
