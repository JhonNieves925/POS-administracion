package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.MovimientoInventario.TipoMovimiento;
import com.distriasociados.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final MovimientoInventarioRepository movimientoRepository;
    private final CompraRepository compraRepository;
    private final CompraDetalleRepository compraDetalleRepository;
    private final ProductoService productoService;
    private final ProductoRepository productoRepository;

    // ─── CONSULTAS ───────────────────────────────────────────

    // Lista todos los productos activos con su stock actual
    public List<Producto> listarInventario() {
        return productoRepository.findByActivoTrue();
    }

    public List<MovimientoInventario> listarMovimientos(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null) fin = LocalDate.now();
        return movimientoRepository.findByFechaBetween(inicio, fin);
    }

    public List<MovimientoInventario> listarMovimientosPorProducto(
            Long productoId, LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().minusMonths(1);
        if (fin == null) fin = LocalDate.now();
        return movimientoRepository.findByProductoIdAndFechaBetween(productoId, inicio, fin);
    }

    public List<Compra> listarCompras(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null) fin = LocalDate.now();
        return compraRepository.findByFechaBetween(inicio, fin);
    }

    // ─── REGISTRAR COMPRA (ingreso de mercancia) ─────────────

    @Transactional
    public Compra registrarCompra(Compra compra, Usuario usuarioActual) {
        compra.setRegistradoPor(usuarioActual);
        compra.setFecha(compra.getFecha() != null ? compra.getFecha() : LocalDate.now());

        var total = compra.getDetalles().stream()
                .map(d -> d.getPrecioCaja()
                        .multiply(java.math.BigDecimal.valueOf(d.getCantidad())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        compra.setTotal(total);

        // ── CUFE: anti-duplicado — si viene CUFE, verificar que no exista ya ──────
        if (compra.getCufe() != null && !compra.getCufe().isBlank()) {
            String cufe = compra.getCufe().trim().toLowerCase();
            compra.setCufe(cufe);
            if (compraRepository.existsByCufe(cufe)) {
                throw new RuntimeException(
                    "Esta factura ya fue registrada anteriormente (CUFE duplicado). " +
                    "No es posible contabilizar la misma factura dos veces.");
            }
        }

        // ── CORRECCIÓN: enlazar cada detalle con la compra padre ANTES de persistir.
        // El JSON del frontend no incluye el campo 'compra' en cada detalle (evita ciclo),
        // por lo que Hibernate lo recibe null y falla con NOT NULL constraint en compra_id.
        // Al setearlo aquí, JPA gestiona correctamente la FK al hacer el INSERT en cascada.
        for (CompraDetalle detalle : compra.getDetalles()) {
            detalle.setCompra(compra);
        }

        Compra compraGuardada = compraRepository.save(compra);

        for (CompraDetalle detalle : compraGuardada.getDetalles()) {
            // Cargar el producto completo (necesitamos unidadesPorCaja para convertir cajas → unidades)
            Producto prod = productoRepository.findById(detalle.getProducto().getId())
                .orElseThrow(() -> new RuntimeException(
                    "Producto no encontrado: " + detalle.getProducto().getId()));

            // stockActual se lleva en UNIDADES (igual que en CargueService).
            // getCantidad() viene en CAJAS → hay que multiplicar por unidadesPorCaja.
            int unidadesIngresadas = detalle.getCantidad() * (prod.getUnidadesPorCaja() != null ? prod.getUnidadesPorCaja() : 1);
            productoService.actualizarStock(prod.getId(), unidadesIngresadas);

            // Actualizar precio de compra del producto para mantener el reporte de ganancias preciso.
            // Con 200+ productos y precios que cambian por proveedor, esto evita actualizar a mano.
            prod.setPrecioCompra(detalle.getPrecioCaja());
            productoRepository.save(prod);

            registrarMovimiento(
                TipoMovimiento.COMPRA, prod, unidadesIngresadas,
                detalle.getPrecioCaja(), compraGuardada.getId(), "COMPRA",
                "Compra a proveedor: " + compra.getProveedor() +
                " (" + detalle.getCantidad() + " cajas × " + prod.getUnidadesPorCaja() + " uds)",
                usuarioActual
            );
        }

        return compraGuardada;
    }

    // ─── ENTRADA / SALIDA DE STOCK (DELTA) ────────────────────
    // cantidad > 0 → entrada de mercancía (suma)
    // cantidad < 0 → salida / merma (resta)

    @Transactional
    public Producto ajustarStock(Long productoId, int cantidad, String motivo, Usuario usuarioActual) {
        Producto prod = productoService.buscarPorId(productoId);
        int stockAnterior = prod.getStockActual();
        int stockNuevo    = stockAnterior + cantidad;

        if (stockNuevo < 0) {
            throw new RuntimeException(
                "Stock insuficiente para: " + prod.getNombreCompleto() +
                ". Stock actual: " + stockAnterior + " uds. Intentó retirar: " + Math.abs(cantidad)
            );
        }

        prod.setStockActual(stockNuevo);
        productoRepository.save(prod);

        TipoMovimiento tipo = cantidad >= 0 ? TipoMovimiento.AJUSTE_POS : TipoMovimiento.AJUSTE_NEG;
        registrarMovimiento(tipo, prod, Math.abs(cantidad), null, null, "AJUSTE",
            motivo + " (anterior: " + stockAnterior + " → nuevo: " + stockNuevo + ")",
            usuarioActual);
        return prod;
    }

    // ─── CORRECCIÓN DE INVENTARIO FÍSICO (SET ABSOLUTO) ───────
    // Úsese solo cuando se hace conteo físico real y se desea fijar el valor exacto.

    @Transactional
    public Producto corregirStockFisico(Long productoId, int stockReal, String motivo, Usuario usuarioActual) {
        Producto prod = productoService.buscarPorId(productoId);
        int anterior  = prod.getStockActual();
        int diferencia = stockReal - anterior;

        prod.setStockActual(stockReal);
        productoRepository.save(prod);

        TipoMovimiento tipo = diferencia >= 0 ? TipoMovimiento.AJUSTE_POS : TipoMovimiento.AJUSTE_NEG;
        registrarMovimiento(tipo, prod, Math.abs(diferencia), null, null, "CORRECCION",
            "Corrección inventario físico: " + motivo +
            " (anterior: " + anterior + " → corrección a: " + stockReal + ")",
            usuarioActual);
        return prod;
    }

    // ─── AJUSTE MANUAL DE INVENTARIO ─────────────────────────

    @Transactional
    public void ajustarInventario(Long productoId, int cantidad, String motivo, Usuario usuarioActual) {
        TipoMovimiento tipo = cantidad > 0 ? TipoMovimiento.AJUSTE_POS : TipoMovimiento.AJUSTE_NEG;
        productoService.actualizarStock(productoId, cantidad);
        Producto producto = productoService.buscarPorId(productoId);
        registrarMovimiento(tipo, producto, Math.abs(cantidad), null, null, "AJUSTE",
            "Ajuste manual: " + motivo, usuarioActual);
    }

    // ─── REGISTRO DE MOVIMIENTOS (METODO INTERNO) ─────────────

    public void registrarMovimiento(
            TipoMovimiento tipo,
            Producto producto,
            int cantidad,
            java.math.BigDecimal precioUnitario,
            Long referenciaId,
            String referenciaTipo,
            String observaciones,
            Usuario usuario) {

        MovimientoInventario movimiento = MovimientoInventario.builder()
                .tipo(tipo)
                .producto(producto)
                .cantidad(cantidad)
                .precioUnitario(precioUnitario)
                .referenciaId(referenciaId)
                .referenciaTipo(referenciaTipo)
                .observaciones(observaciones)
                .usuario(usuario)
                .fecha(LocalDate.now())
                .build();

        movimientoRepository.save(movimiento);
    }
}
