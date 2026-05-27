package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Cargue.EstadoCargue;
import com.distriasociados.inventario.entity.MovimientoInventario.TipoMovimiento;
import com.distriasociados.inventario.entity.Remision.EstadoRemision;
import com.distriasociados.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CargueService {

    private final CargueRepository cargueRepository;
    private final CargueDetalleRepository cargueDetalleRepository;
    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final VendedorRepository vendedorRepository;
    private final RutaRepository rutaRepository;
    private final RemisionRepository remisionRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    // ─── CONSULTAS ───────────────────────────────────────────

    // Todos los cargues del día — para el dashboard
    public List<Cargue> carguesDelDia() {
        return cargueRepository.findByFecha(LocalDate.now());
    }

    // Cargue activo de un vendedor hoy - retorna null si no existe
    public Cargue cargueActivoVendedor(Long vendedorId) {
        List<Cargue> activos = cargueRepository
                .findCargueActivoVendedor(vendedorId, LocalDate.now());
        return activos.isEmpty() ? null : activos.get(0);
    }

    public Cargue buscarPorId(Long id) {
        return cargueRepository.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Cargue no encontrado")
                );
    }

    public List<Cargue> listarPorFecha(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null) fin = LocalDate.now();
        return cargueRepository.findByFechaBetween(inicio, fin);
    }

    // ─── CREAR CARGUE ────────────────────────────────────────

    @Transactional
    public Cargue crearCargue(Cargue cargue, Usuario usuarioActual) {

        // Verificar que el vendedor no tenga ya un cargue activo hoy
        List<Cargue> activosHoy = cargueRepository
            .findCargueActivoVendedor(cargue.getVendedor().getId(), LocalDate.now());
        if (!activosHoy.isEmpty()) {
            throw new RuntimeException(
                "El vendedor ya tiene un cargue activo hoy: " + activosHoy.get(0).getNumero());
        }

        // Usar la fecha enviada por el frontend; si no viene, usar hoy
        if (cargue.getFecha() == null) cargue.setFecha(LocalDate.now());
        // Generar número usando la fecha real del cargue
        cargue.setNumero(generarNumeroCargue(cargue.getFecha()));
        cargue.setCreadoPor(usuarioActual);
        cargue.setEstado(EstadoCargue.CREADO);

        // Calcular total de unidades y preparar detalles ANTES de guardar
        int totalUnidades = 0;
        for (CargueDetalle detalle : cargue.getDetalles()) {
            // Asignar referencia al padre ANTES del save (evita cargue_id=null en cascade)
            detalle.setCargue(cargue);
            totalUnidades += detalle.getCantidadCargue();
        }
        cargue.setTotalUnidadesCargue(totalUnidades);

        // Guardar cargue (CASCADE inserta detalles con cargue_id correcto)
        Cargue cargueGuardado = cargueRepository.save(cargue);

        // Por cada producto → descontar del stock + registrar movimiento
        for (CargueDetalle detalle : cargueGuardado.getDetalles()) {

            // Tomar el precio actual del producto como snapshot
            Producto producto = productoService
                    .buscarPorId(detalle.getProducto().getId());
            detalle.setPrecioCajaSnapshot(producto.getPrecioCaja());

            // Descontar stock (negativo porque sale de bodega)
            productoService.actualizarStock(
                producto.getId(),
                -detalle.getCantidadCargue()
            );

            // Registrar en historial
            inventarioService.registrarMovimiento(
                TipoMovimiento.CARGUE,
                producto,
                detalle.getCantidadCargue(),
                producto.getPrecioCaja(),
                cargueGuardado.getId(),
                "CARGUE",
                "Cargue " + cargueGuardado.getNumero() +
                        " — Vendedor: " + cargueGuardado.getVendedor().getNombre(),
                usuarioActual
            );
        }

        return cargueGuardado;
    }

    // ─── VENDEDOR SALE A RUTA ────────────────────────────────

    @Transactional
    public Cargue marcarEnRuta(Long cargueId) {
        Cargue cargue = buscarPorId(cargueId);

        if (cargue.getEstado() != EstadoCargue.CREADO) {
            throw new RuntimeException(
                "El cargue debe estar en estado CREADO para salir a ruta"
            );
        }

        cargue.setEstado(EstadoCargue.EN_RUTA);
        return cargueRepository.save(cargue);
    }

    // ─── VENDEDOR REGISTRA DEVOLUCIONES ─────────────────────
    // Lo hace el vendedor desde la PWA o la auxiliar en bodega

    @Transactional
    public Cargue registrarDevoluciones(
            Long cargueId,
            List<CargueDetalle> devoluciones) {

        Cargue cargue = buscarPorId(cargueId);

        if (cargue.getEstado() == EstadoCargue.LIQUIDADO) {
            throw new RuntimeException("El cargue ya fue liquidado");
        }

        // Actualizar las devoluciones por producto
        for (CargueDetalle devolucion : devoluciones) {
            CargueDetalle detalleExistente = cargue.getDetalles().stream()
                    .filter(d -> d.getId().equals(devolucion.getId()))
                    .findFirst()
                    .orElseThrow(() ->
                        new RuntimeException("Detalle de cargue no encontrado")
                    );

            // Validar que no devuelva más de lo que llevó
            if (devolucion.getCantidadDevolucion() >
                    detalleExistente.getCantidadCargue()) {
                throw new RuntimeException(
                    "La devolución no puede ser mayor al cargue para: " +
                    detalleExistente.getProducto().getNombreCompleto()
                );
            }

            detalleExistente.setCantidadDevolucion(
                devolucion.getCantidadDevolucion()
            );
        }

        cargue.setEstado(EstadoCargue.DEVUELTO);

        // Calcular total de devoluciones
        int totalDevoluciones = cargue.getDetalles().stream()
                .mapToInt(CargueDetalle::getCantidadDevolucion)
                .sum();
        cargue.setTotalUnidadesDevolucion(totalDevoluciones);

        return cargueRepository.save(cargue);
    }

    // ─── LIQUIDAR CARGUE ─────────────────────────────────────
    // La auxiliar confirma y cierra el cargue

    @Transactional
    public Cargue liquidar(Long cargueId, Usuario usuarioActual) {
        Cargue cargue = buscarPorId(cargueId);

        if (cargue.getEstado() == EstadoCargue.LIQUIDADO) {
            throw new RuntimeException("El cargue ya está liquidado");
        }

        // ── 1. Calcular lo realmente vendido por producto (solo remisiones no anuladas)
        List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(cargueId)
            .stream()
            .filter(r -> r.getEstado() != Remision.EstadoRemision.ANULADA)
            .toList();

        // productoId → unidades con remisión registrada
        Map<Long, Integer> vendidoConRemision = new HashMap<>();
        for (Remision r : remisiones) {
            for (RemisionDetalle det : r.getDetalles()) {
                Long pid = det.getProducto().getId();
                int qty = det.getCantidad() != null ? det.getCantidad().intValue() : 0;
                vendidoConRemision.merge(pid, qty, Integer::sum);
            }
        }

        // ── 2. Por cada producto: calcular devolución real y actualizar stock
        int totalDevuelto = 0;
        for (CargueDetalle detalle : cargue.getDetalles()) {
            Long productoId   = detalle.getProducto().getId();
            int  cargado      = detalle.getCantidadCargue() != null ? detalle.getCantidadCargue() : 0;
            int  vendido      = vendidoConRemision.getOrDefault(productoId, 0);
            int  aDevolver    = Math.max(0, cargado - vendido);

            // Guardar el valor calculado en el detalle (para historial y reportes)
            detalle.setCantidadDevolucion(aDevolver);

            if (aDevolver > 0) {
                productoService.actualizarStock(productoId, aDevolver);
                inventarioService.registrarMovimiento(
                    TipoMovimiento.DEVOLUCION,
                    detalle.getProducto(),
                    aDevolver,
                    detalle.getPrecioCajaSnapshot(),
                    cargue.getId(),
                    "CARGUE",
                    "Liquidación " + cargue.getNumero() +
                    " — cargado: " + cargado + " | vendido: " + vendido + " | devuelto: " + aDevolver,
                    usuarioActual
                );
                totalDevuelto += aDevolver;
            }
        }

        cargue.setTotalUnidadesDevolucion(totalDevuelto);
        cargue.setEstado(EstadoCargue.LIQUIDADO);
        cargue.setLiquidadoEn(java.time.LocalDateTime.now());
        return cargueRepository.save(cargue);
    }

    // ─── METODOS DE CONVENIENCIA PARA REQUESTS DEL FRONTEND ──

    // Crea el cargue desde un Map (request JSON del frontend)
    @Transactional
    public Cargue crearDesdeRequest(Map<String, Object> body, Usuario usuarioActual) {
        Long vendedorId = Long.valueOf(body.get("vendedorId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detallesRaw = (List<Map<String, Object>>) body.get("detalles");

        Vendedor vendedor = vendedorRepository.findById(vendedorId)
            .orElseThrow(() -> new RuntimeException("Vendedor no encontrado: " + vendedorId));

        Cargue cargue = new Cargue();
        cargue.setVendedor(vendedor);

        // Fecha del cargue: usar la enviada por el frontend o hoy
        String fechaStr = body.getOrDefault("fecha", "").toString();
        if (!fechaStr.isBlank()) {
            cargue.setFecha(LocalDate.parse(fechaStr));
        }

        // Usar la ruta principal del vendedor si no se especifica
        if (vendedor.getRutaPrincipal() != null) {
            cargue.setRuta(vendedor.getRutaPrincipal());
        } else {
            // Buscar cualquier ruta disponible como fallback
            rutaRepository.findAll().stream().findFirst()
                .ifPresent(cargue::setRuta);
        }

        List<CargueDetalle> detalles = new ArrayList<>();
        for (Map<String, Object> d : detallesRaw) {
            CargueDetalle det = new CargueDetalle();
            Long productoId = Long.valueOf(d.get("productoId").toString());
            Producto prodReal = productoService.buscarPorId(productoId);
            det.setProducto(prodReal);
            // Snapshot del precio ANTES de guardar (columna NOT NULL)
            det.setPrecioCajaSnapshot(prodReal.getPrecioCaja());
            int cajas = Integer.parseInt(d.getOrDefault("cantidadCajas", 0).toString());
            int unidades = Integer.parseInt(d.getOrDefault("cantidadUnidades", 0).toString());
            det.setCantidadCargue(cajas * prodReal.getUnidadesPorCaja() + unidades);
            det.setCantidadDevolucion(0);
            detalles.add(det);
        }
        cargue.setDetalles(detalles);
        return crearCargue(cargue, usuarioActual);
    }

    // Registra devoluciones desde Map (request JSON del frontend)
    @Transactional
    public Cargue registrarDevolucionesDesdeRequest(Long cargueId, List<Map<String, Object>> devoluciones) {
        List<CargueDetalle> lista = new ArrayList<>();
        for (Map<String, Object> d : devoluciones) {
            CargueDetalle det = new CargueDetalle();
            det.setId(Long.valueOf(d.get("detalleId").toString()));
            det.setCantidadDevolucion(Integer.parseInt(d.getOrDefault("cantidadDevolucion", 0).toString()));
            lista.add(det);
        }
        return registrarDevoluciones(cargueId, lista);
    }

    // Cargue activo del vendedor sin lanzar excepcion (retorna null si no existe)
    public Cargue cargueActivoVendedorONull(Long vendedorId) {
        List<Cargue> activos = cargueRepository
            .findCargueActivoVendedor(vendedorId, LocalDate.now());
        return activos.isEmpty() ? null : activos.get(0);
    }

    // ─── DUPLICAR CARGUE PARA VARIOS VENDEDORES ─────────────
    // Los cargues duplicados comparten el mismo grupoId y el mismo pool de stock.
    // NO se descuenta stock adicional — el stock ya salió con el cargue original.

    @Transactional
    public List<Cargue> duplicar(Long cargueId, List<Long> vendedorIds, Usuario usuarioActual) {
        Cargue original = buscarPorId(cargueId);

        if (original.getEstado() == EstadoCargue.LIQUIDADO) {
            throw new RuntimeException("No se puede duplicar un cargue ya liquidado");
        }

        // El grupoId del grupo es el id del cargue original
        Long grupoId = original.getId();
        if (original.getGrupoId() == null) {
            original.setGrupoId(grupoId);
            cargueRepository.save(original);
        } else {
            // Ya pertenece a un grupo — usar el grupoId existente
            grupoId = original.getGrupoId();
        }

        final Long gId = grupoId;
        List<Cargue> creados = new ArrayList<>();

        for (Long vendedorId : vendedorIds) {
            Vendedor vendedor = vendedorRepository.findById(vendedorId)
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado: " + vendedorId));

            List<Cargue> activosVend = cargueRepository.findCargueActivoVendedor(vendedorId, LocalDate.now());
            if (!activosVend.isEmpty()) {
                throw new RuntimeException(
                    "El vendedor " + vendedor.getNombre() + " ya tiene cargue activo hoy: " + activosVend.get(0).getNumero());
            }

            Cargue nuevo = new Cargue();
            nuevo.setVendedor(vendedor);
            nuevo.setFecha(original.getFecha());
            nuevo.setRuta(original.getRuta());
            nuevo.setCreadoPor(usuarioActual);
            nuevo.setEstado(EstadoCargue.CREADO);
            nuevo.setGrupoId(gId);
            nuevo.setNumero(generarNumeroCargue(nuevo.getFecha()));

            int totalUnidades = 0;
            List<CargueDetalle> detalles = new ArrayList<>();
            for (CargueDetalle orig : original.getDetalles()) {
                CargueDetalle det = new CargueDetalle();
                det.setCargue(nuevo);
                det.setProducto(orig.getProducto());
                det.setCantidadCargue(orig.getCantidadCargue());
                det.setCantidadDevolucion(0);
                det.setPrecioCajaSnapshot(orig.getPrecioCajaSnapshot());
                detalles.add(det);
                totalUnidades += orig.getCantidadCargue();
            }
            nuevo.setDetalles(detalles);
            nuevo.setTotalUnidadesCargue(totalUnidades);

            creados.add(cargueRepository.save(nuevo));
        }

        return creados;
    }

    // ─── VENDIDO POR GRUPO ───────────────────────────────────
    // Devuelve mapa productoId → total unidades vendidas en TODO el grupo
    // (excluyendo remisiones anuladas)

    public Map<Long, Integer> grupoVendidoMap(Long cargueId) {
        Cargue cargue = buscarPorId(cargueId);
        Long grupoId = cargue.getGrupoId();

        List<Long> ids;
        if (grupoId != null) {
            ids = cargueRepository.findByGrupoId(grupoId)
                .stream().map(Cargue::getId).collect(Collectors.toList());
        } else {
            ids = List.of(cargueId);
        }

        Map<Long, Integer> mapa = new HashMap<>();
        for (Remision rem : remisionRepository.findByCargueIdIn(ids)) {
            if (rem.getEstado() == EstadoRemision.ANULADA) continue;
            for (RemisionDetalle det : rem.getDetalles()) {
                Long pid = det.getProducto().getId();
                mapa.merge(pid, det.getCantidad().intValue(), Integer::sum);
            }
        }
        return mapa;
    }

    // ─── ELIMINACIÓN ────────────────────────────────────────

    /**
     * Elimina un cargue del historial.
     *
     * Regla de stock según estado:
     *  · NO LIQUIDADO (CREADO / EN_RUTA / DEVUELTO):
     *      El vendedor aún no cerró ruta → revertir el descuento de bodega
     *      (los productos vuelven al inventario porque la venta no se concretó).
     *  · LIQUIDADO:
     *      El cargue ya fue cerrado, la devolución ya se procesó y las remisiones
     *      ya se facturaron a la DIAN.  El stock ya es correcto.
     *      Solo se limpia el historial — NO se toca el inventario.
     */
    @Transactional
    public Map<String, Object> eliminarCargue(Long cargueId) {
        Cargue cargue = buscarPorId(cargueId);

        List<MovimientoInventario> todosMovimientos =
            movimientoRepository.findByReferenciaIdAndReferenciaTipo(cargueId, "CARGUE");

        int unidadesDevueltas = 0;

        if (cargue.getEstado() != EstadoCargue.LIQUIDADO) {
            // Cargue aún no liquidado → devolver al stock todo lo que salió de bodega
            for (MovimientoInventario mov : todosMovimientos) {
                if (mov.getTipo() == TipoMovimiento.CARGUE) {
                    productoService.actualizarStock(mov.getProducto().getId(), mov.getCantidad());
                    unidadesDevueltas += mov.getCantidad();
                }
            }
        }
        // Si está LIQUIDADO: stock ya está en su estado correcto → no hacer nada

        // Borrar movimientos del kardex
        movimientoRepository.deleteAll(todosMovimientos);

        // Eliminar remisiones una a una (deleteById recarga managed → cascade abonos/detalles)
        List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(cargueId);
        for (Remision r : remisiones) {
            remisionRepository.deleteById(r.getId());
        }

        // Eliminar el cargue (cascade elimina cargue_detalles)
        cargueRepository.deleteById(cargueId);

        return Map.of(
            "mensaje", cargue.getEstado() == EstadoCargue.LIQUIDADO
                ? "Historial del cargue eliminado (stock sin cambios — ya liquidado)"
                : "Cargue eliminado y stock restaurado",
            "numero", cargue.getNumero(),
            "unidadesDevueltas", unidadesDevueltas,
            "remisionesEliminadas", remisiones.size()
        );
    }

    /**
     * Elimina todos los cargues revirtiendo stock y kardex de cada uno.
     */
    @Transactional
    public Map<String, Object> eliminarTodosCargues() {
        List<Cargue> todos = cargueRepository.findAll();
        int totalRemisiones = 0;
        int totalUnidadesDevueltas = 0;

        for (Cargue c : todos) {
            List<MovimientoInventario> movimientos =
                movimientoRepository.findByReferenciaIdAndReferenciaTipo(c.getId(), "CARGUE");

            // Solo revertir stock si el cargue NO está liquidado
            // (liquidado = ya facturado a DIAN, stock ya es correcto)
            if (c.getEstado() != EstadoCargue.LIQUIDADO) {
                for (MovimientoInventario mov : movimientos) {
                    if (mov.getTipo() == TipoMovimiento.CARGUE) {
                        productoService.actualizarStock(mov.getProducto().getId(), mov.getCantidad());
                        totalUnidadesDevueltas += mov.getCantidad();
                    }
                }
            }

            movimientoRepository.deleteAll(movimientos);

            // Eliminar remisiones una a una (deleteById recarga managed → cascade abonos/detalles)
            List<Remision> remisiones = remisionRepository.findByCargueIdOrderByIdDesc(c.getId());
            for (Remision r : remisiones) {
                remisionRepository.deleteById(r.getId());
            }
            totalRemisiones += remisiones.size();
        }

        // Eliminar todos los cargues (cascade elimina cargue_detalles)
        for (Cargue c : todos) {
            cargueRepository.deleteById(c.getId());
        }

        return Map.of(
            "mensaje", "Historial limpiado",
            "carguesEliminados", todos.size(),
            "remisionesEliminadas", totalRemisiones,
            "unidadesDevueltas", totalUnidadesDevueltas
        );
    }

    // ─── GENERAR NÚMERO ÚNICO ────────────────────────────────

    private String generarNumeroCargue(LocalDate fecha) {
        String fechaStr = fecha.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Contar cuántos cargues hay ese día para el consecutivo
        long consecutivo = cargueRepository.findByFecha(fecha).size() + 1;

        // Formato: CAR-20260429-001
        return String.format("CAR-%s-%03d", fechaStr, consecutivo);
    }
}