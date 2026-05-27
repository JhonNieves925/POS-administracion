package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.facturacion.SiigoConfigDto;
import com.distriasociados.inventario.dto.facturacion.SiigoProductoDto;
import com.distriasociados.inventario.dto.facturacion.SiigoSyncResultDto;
import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.repository.ProductoRepository;
import com.distriasociados.inventario.service.SiigoSincronizacionService;
import com.distriasociados.inventario.service.facturacion.SiigoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Operaciones de administración relacionadas con Siigo:
 *   POST /api/siigo/sincronizar-productos
 *
 * Solo accesible para ADMIN.
 */
@Slf4j
@RestController
@RequestMapping("/api/siigo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SiigoAdminController {

    private final SiigoSincronizacionService sincronizacionService;
    private final SiigoPort siigoPort;
    private final ProductoRepository productoRepository;

    /**
     * Consulta los IDs de configuración de la cuenta Siigo (tipos de documento,
     * impuestos, métodos de pago). Se usa una sola vez para configurar las
     * variables de entorno en producción.
     * En modo mock retorna un mensaje explicativo sin llamar a Siigo.
     */
    @GetMapping("/configuracion")
    public ResponseEntity<ApiResponse<SiigoConfigDto>> consultarConfiguracion() {
        log.info("[ADMIN] Consultando configuración de IDs de Siigo");
        SiigoConfigDto config = siigoPort.consultarConfiguracion();
        return ResponseEntity.ok(ApiResponse.ok("Configuración obtenida", config));
    }

    /**
     * Devuelve el catálogo completo de productos de Siigo con un flag
     * 'yaEnSistema' para que el frontend sepa cuáles ya están en la BD local.
     * No modifica nada — es solo lectura.
     */
    @GetMapping("/productos")
    public ResponseEntity<ApiResponse<List<SiigoProductoDto>>> listarProductosSiigo() {
        log.info("[ADMIN] Listando catálogo de productos de Siigo");
        List<SiigoProductoDto> productos = siigoPort.listarProductos();

        // Marcar cuáles ya existen localmente (por siigoCodigo o por codigo)
        productos.forEach(dto -> {
            boolean existe = productoRepository.findBySiigoCodigo(dto.getCodigo()).isPresent()
                          || productoRepository.findByCodigo(dto.getCodigo()).isPresent();
            dto.setYaEnSistema(existe);
        });

        return ResponseEntity.ok(ApiResponse.ok("Catálogo obtenido", productos));
    }

    /**
     * Conecta con Siigo, descarga el catálogo completo de productos y los
     * sincroniza con la BD local.
     *
     * - Productos existentes: actualiza siigoCodigo y tipoIva si cambiaron.
     * - Productos nuevos:     crea con activo=true.
     * - Modo mock:            retorna resultado vacío sin llamar a Siigo.
     */
    @PostMapping("/sincronizar-productos")
    public ResponseEntity<ApiResponse<SiigoSyncResultDto>> sincronizarProductos() {
        log.info("[ADMIN] Iniciando sincronización de productos desde Siigo");
        SiigoSyncResultDto resultado = sincronizacionService.sincronizarProductos();
        return ResponseEntity.ok(ApiResponse.ok("Sincronización completada", resultado));
    }

    /**
     * Sincroniza un único producto por código exacto de Siigo.
     * Si el código no existe en Siigo, retorna error descriptivo.
     *
     * Ejemplo: POST /api/siigo/sincronizar-producto?codigo=ABC123
     */
    @PostMapping("/sincronizar-producto")
    public ResponseEntity<ApiResponse<SiigoSyncResultDto>> sincronizarProducto(
            @RequestParam @NotBlank String codigo) {
        log.info("[ADMIN] Sincronizando producto individual: {}", codigo);
        SiigoSyncResultDto resultado = sincronizacionService.sincronizarProductoPorCodigo(codigo.trim());
        String mensaje = resultado.getErrores() > 0
            ? "No se pudo sincronizar el producto"
            : resultado.getCreados() > 0
                ? "Producto creado correctamente"
                : resultado.getActualizados() > 0
                    ? "Producto actualizado correctamente"
                    : "Producto ya estaba sincronizado (sin cambios)";
        return ResponseEntity.ok(ApiResponse.ok(mensaje, resultado));
    }
}
