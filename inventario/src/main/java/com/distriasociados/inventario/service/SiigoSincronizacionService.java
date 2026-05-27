package com.distriasociados.inventario.service;

import com.distriasociados.inventario.dto.facturacion.SiigoProductoDto;
import com.distriasociados.inventario.dto.facturacion.SiigoSyncResultDto;
import com.distriasociados.inventario.entity.Producto;
import com.distriasociados.inventario.repository.ProductoRepository;
import com.distriasociados.inventario.service.facturacion.SiigoPort;
import com.distriasociados.inventario.util.SiigoNombreParser;
import com.distriasociados.inventario.util.SiigoNombreParser.ParsedProducto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sincroniza el catálogo de productos de Siigo con la base de datos local.
 *
 * Reglas de matching (en orden):
 *   1. Por siigoCodigo (= code de Siigo) → ya fue sincronizado antes
 *   2. Por codigo local        → código barras coincide con el code de Siigo
 *   3. Sin coincidencia        → crea el producto parseando el nombre automáticamente
 *
 * Qué se actualiza en productos existentes:
 *   - siigoCodigo (si no estaba asignado)
 *   - tipoIva     (se toma de Siigo como fuente de verdad)
 *
 * Qué NO se toca en existentes:
 *   - Stock, unidadesPorCaja, precioCompra, IBUA, activo, stockMinimo
 *   (datos de inventario manejados internamente)
 *
 * Productos nuevos: el nombre de Siigo se parsea automáticamente con
 * {@link SiigoNombreParser} extrayendo marca, sabor, tamanoMl, unidadMedida,
 * presentacion y unidadesPorCaja.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiigoSincronizacionService {

    private final SiigoPort siigoPort;
    private final ProductoRepository productoRepository;

    @Transactional
    public SiigoSyncResultDto sincronizarProductos() {

        // ── 1. Traer catálogo de Siigo ────────────────────────────
        List<SiigoProductoDto> remotos;
        try {
            remotos = siigoPort.listarProductos();
        } catch (Exception e) {
            log.error("[SIIGO SYNC] Error al conectar con Siigo: {}", e.getMessage());
            return SiigoSyncResultDto.builder()
                .actualizados(0).creados(0).sinCambios(0).errores(1)
                .productosCreados(List.of())
                .mensajesError(List.of("No se pudo conectar con Siigo: " + e.getMessage()))
                .build();
        }

        if (remotos.isEmpty()) {
            log.info("[SIIGO SYNC] Siigo devolvió lista vacía (modo mock o catálogo vacío)");
            return SiigoSyncResultDto.builder()
                .actualizados(0).creados(0).sinCambios(0).errores(0)
                .productosCreados(List.of()).mensajesError(List.of())
                .build();
        }

        // ── 2. Procesar cada producto ────────────────────────────
        int actualizados = 0, creados = 0, sinCambios = 0, errores = 0;
        List<String> productosCreados  = new ArrayList<>();
        List<String> mensajesError     = new ArrayList<>();

        for (SiigoProductoDto dto : remotos) {
            try {
                // Buscar por siigoCodigo primero (sync repetido)
                Optional<Producto> existente = productoRepository.findBySiigoCodigo(dto.getCodigo());

                // Si no, buscar por código local que coincida con el code de Siigo
                if (existente.isEmpty()) {
                    existente = productoRepository.findByCodigo(dto.getCodigo());
                }

                Producto.TipoIva tipoIva = calcularTipoIva(dto.getPorcentajeIva());

                if (existente.isPresent()) {
                    // ── Producto encontrado: actualizar campos Siigo ──────
                    Producto p = existente.get();
                    boolean cambio = false;

                    if (!dto.getCodigo().equals(p.getSiigoCodigo())) {
                        p.setSiigoCodigo(dto.getCodigo());
                        cambio = true;
                    }
                    if (p.getTipoIva() != tipoIva) {
                        p.setTipoIva(tipoIva);
                        cambio = true;
                    }

                    if (cambio) {
                        productoRepository.save(p);
                        actualizados++;
                        log.debug("[SIIGO SYNC] Actualizado: {} [{}]", p.getNombreCompleto(), dto.getCodigo());
                    } else {
                        sinCambios++;
                    }

                } else {
                    // ── Producto nuevo: parsear nombre y crear ────────────
                    Producto nuevo = construirNuevoProducto(dto, tipoIva);
                    productoRepository.save(nuevo);
                    creados++;
                    productosCreados.add(dto.getNombre() + " [" + dto.getCodigo() + "]");
                    log.debug("[SIIGO SYNC] Creado: {} → marca='{}' sabor='{}' tam='{}{}' pres={} uds={}",
                        dto.getCodigo(), nuevo.getMarca(), nuevo.getSabor(),
                        nuevo.getTamanoMl(), nuevo.getUnidadMedida(),
                        nuevo.getPresentacion(), nuevo.getUnidadesPorCaja());
                }

            } catch (Exception e) {
                log.error("[SIIGO SYNC] Error procesando producto {}: {}", dto.getCodigo(), e.getMessage());
                errores++;
                mensajesError.add("[" + dto.getCodigo() + "] " + e.getMessage());
            }
        }

        log.info("[SIIGO SYNC] Completado — Actualizados: {}, Creados: {}, Sin cambios: {}, Errores: {}",
            actualizados, creados, sinCambios, errores);

        return SiigoSyncResultDto.builder()
            .actualizados(actualizados)
            .creados(creados)
            .sinCambios(sinCambios)
            .errores(errores)
            .productosCreados(productosCreados)
            .mensajesError(mensajesError)
            .build();
    }

    /**
     * Sincroniza un único producto buscándolo en el catálogo de Siigo por código.
     * Si no existe en Siigo, retorna un resultado con 1 error y mensaje descriptivo.
     */
    @Transactional
    public SiigoSyncResultDto sincronizarProductoPorCodigo(String codigo) {

        // ── 1. Traer catálogo de Siigo y buscar el código ─────────
        List<SiigoProductoDto> remotos;
        try {
            remotos = siigoPort.listarProductos();
        } catch (Exception e) {
            log.error("[SIIGO SYNC UNO] Error al conectar con Siigo: {}", e.getMessage());
            return SiigoSyncResultDto.builder()
                .actualizados(0).creados(0).sinCambios(0).errores(1)
                .productosCreados(List.of())
                .mensajesError(List.of("No se pudo conectar con Siigo: " + e.getMessage()))
                .build();
        }

        if (remotos.isEmpty()) {
            log.info("[SIIGO SYNC UNO] Siigo devolvió lista vacía (modo mock)");
            return SiigoSyncResultDto.builder()
                .actualizados(0).creados(0).sinCambios(0).errores(0)
                .productosCreados(List.of()).mensajesError(List.of())
                .build();
        }

        SiigoProductoDto dto = remotos.stream()
            .filter(p -> codigo.equalsIgnoreCase(p.getCodigo()))
            .findFirst()
            .orElse(null);

        if (dto == null) {
            log.warn("[SIIGO SYNC UNO] Código '{}' no encontrado en Siigo", codigo);
            return SiigoSyncResultDto.builder()
                .actualizados(0).creados(0).sinCambios(0).errores(1)
                .productosCreados(List.of())
                .mensajesError(List.of("No se encontró el código '" + codigo + "' en el catálogo de Siigo"))
                .build();
        }

        // ── 2. Procesar ese único producto ────────────────────────
        int actualizados = 0, creados = 0, sinCambios = 0, errores = 0;
        List<String> productosCreados = new ArrayList<>();
        List<String> mensajesError    = new ArrayList<>();

        try {
            Optional<Producto> existente = productoRepository.findBySiigoCodigo(dto.getCodigo());
            if (existente.isEmpty()) {
                existente = productoRepository.findByCodigo(dto.getCodigo());
            }

            Producto.TipoIva tipoIva = calcularTipoIva(dto.getPorcentajeIva());

            if (existente.isPresent()) {
                Producto p = existente.get();
                boolean cambio = false;
                if (!dto.getCodigo().equals(p.getSiigoCodigo())) {
                    p.setSiigoCodigo(dto.getCodigo());
                    cambio = true;
                }
                if (p.getTipoIva() != tipoIva) {
                    p.setTipoIva(tipoIva);
                    cambio = true;
                }
                if (cambio) {
                    productoRepository.save(p);
                    actualizados++;
                    log.info("[SIIGO SYNC UNO] Actualizado: {} [{}]", p.getNombreCompleto(), dto.getCodigo());
                } else {
                    sinCambios++;
                    log.info("[SIIGO SYNC UNO] Sin cambios: {} [{}]", p.getNombreCompleto(), dto.getCodigo());
                }
            } else {
                Producto nuevo = construirNuevoProducto(dto, tipoIva);
                productoRepository.save(nuevo);
                creados++;
                productosCreados.add(dto.getNombre() + " [" + dto.getCodigo() + "]");
                log.info("[SIIGO SYNC UNO] Creado: {} → marca='{}' sabor='{}' tam='{}{}' pres={} uds={}",
                    dto.getCodigo(), nuevo.getMarca(), nuevo.getSabor(),
                    nuevo.getTamanoMl(), nuevo.getUnidadMedida(),
                    nuevo.getPresentacion(), nuevo.getUnidadesPorCaja());
            }
        } catch (Exception e) {
            log.error("[SIIGO SYNC UNO] Error procesando producto {}: {}", dto.getCodigo(), e.getMessage());
            errores++;
            mensajesError.add("[" + dto.getCodigo() + "] " + e.getMessage());
        }

        return SiigoSyncResultDto.builder()
            .actualizados(actualizados)
            .creados(creados)
            .sinCambios(sinCambios)
            .errores(errores)
            .productosCreados(productosCreados)
            .mensajesError(mensajesError)
            .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Construye un Producto nuevo a partir de un DTO de Siigo.
     * Usa {@link SiigoNombreParser} para extraer marca, sabor, tamaño,
     * unidad, presentación y unidades por caja del nombre del producto.
     * El precio de lista de Siigo se asigna como precioCaja inicial.
     */
    private Producto construirNuevoProducto(SiigoProductoDto dto, Producto.TipoIva tipoIva) {
        ParsedProducto parsed = SiigoNombreParser.parse(dto.getNombre());

        BigDecimal precio = dto.getPrecio() != null && dto.getPrecio().compareTo(BigDecimal.ZERO) > 0
            ? dto.getPrecio() : BigDecimal.ONE;

        return Producto.builder()
            .codigo(dto.getCodigo())
            .siigoCodigo(dto.getCodigo())
            // ── Campos parseados automáticamente del nombre ──────────
            .marca(parsed.getMarca())
            .sabor(parsed.getSabor())
            .tamanoMl(parsed.getTamanoMl())
            .unidadMedida(parsed.getUnidadMedida())
            .presentacion(parsed.getPresentacion())
            .unidadesPorCaja(parsed.getUnidadesPorCaja())
            // ── Campos que el admin debe completar luego ─────────────
            .precioCaja(precio)          // precio de lista Siigo como referencia inicial
            .precioCompra(null)          // costo proveedor — completar manualmente
            .tipoIva(tipoIva)
            .valorIbuaUnidad(BigDecimal.ZERO)
            .stockActual(0)
            .stockMinimo(0)
            .activo(true)
            .build();
    }

    private Producto.TipoIva calcularTipoIva(double pct) {
        if (pct >= 19) return Producto.TipoIva.IVA_19;
        if (pct >= 5)  return Producto.TipoIva.IVA_5;
        // 0% puede ser EXCLUIDO o EXENTO; usamos EXCLUIDO como default conservador
        return Producto.TipoIva.EXCLUIDO;
    }
}
