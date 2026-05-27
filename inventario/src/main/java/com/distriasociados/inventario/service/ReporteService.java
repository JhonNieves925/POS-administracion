package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.CargueDetalle;
import com.distriasociados.inventario.entity.Factura;
import com.distriasociados.inventario.entity.MovimientoInventario.TipoMovimiento;
import com.distriasociados.inventario.entity.NotaCredito;
import com.distriasociados.inventario.entity.NotaDebito;
import com.distriasociados.inventario.entity.Producto;
import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.entity.Remision.EstadoRemision;
import com.distriasociados.inventario.entity.Remision.FormaPago;
import com.distriasociados.inventario.entity.RemisionDetalle;
import com.distriasociados.inventario.repository.CargueDetalleRepository;
import com.distriasociados.inventario.repository.FacturaRepository;
import com.distriasociados.inventario.repository.MovimientoInventarioRepository;
import com.distriasociados.inventario.repository.NotaCreditoRepository;
import com.distriasociados.inventario.repository.NotaDebitoRepository;
import com.distriasociados.inventario.repository.ProductoRepository;
import com.distriasociados.inventario.repository.RemisionRepository;
import com.distriasociados.inventario.service.ConfiguracionContableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final CargueDetalleRepository       cargueDetalleRepository;
    private final ProductoRepository            productoRepository;
    private final RemisionRepository            remisionRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final FacturaRepository             facturaRepository;
    private final NotaCreditoRepository         notaCreditoRepository;
    private final NotaDebitoRepository          notaDebitoRepository;
    private final ConfiguracionContableService  contableService;

    // Reporte de ventas por rango de fechas
    public List<Map<String, Object>> reporteVentas(LocalDate desde, LocalDate hasta, Long vendedorId) {
        List<CargueDetalle> detalles;
        if (vendedorId != null) {
            detalles = cargueDetalleRepository.findByFechaRangoYVendedor(desde, hasta, vendedorId);
        } else {
            detalles = cargueDetalleRepository.findByFechaRango(desde, hasta);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (CargueDetalle d : detalles) {
            if (d.getCantidadVenta() <= 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fecha", d.getCargue().getFecha());
            row.put("numeroCargue", d.getCargue().getNumero());
            row.put("vendedor", d.getCargue().getVendedor() != null ? d.getCargue().getVendedor().getNombre() : "-");
            row.put("producto", d.getProducto() != null ? d.getProducto().getNombreCompleto() : "-");
            row.put("cantidadVendida", d.getCantidadVenta());
            row.put("subtotal", d.getSubtotalVenta() != null ? d.getSubtotalVenta() : 0);
            resultado.add(row);
        }
        return resultado;
    }

    // Reporte de inventario actual
    public List<Map<String, Object>> reporteInventario() {
        List<Producto> productos = productoRepository.findByActivoTrue();
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Producto p : productos) {
            Map<String, Object> row = new LinkedHashMap<>();
            int stock = p.getStockActual() != null ? p.getStockActual() : 0;
            int upk = p.getUnidadesPorCaja() > 0 ? p.getUnidadesPorCaja() : 1;
            row.put("idProducto", p.getId());
            row.put("nombreCompleto", p.getNombreCompleto());
            row.put("stockActual", stock);
            row.put("stockMinimo", p.getStockMinimo());
            row.put("cantidadCajas", stock / upk);
            row.put("cantidadUnidades", stock % upk);
            double valor = stock * (p.getPrecioCaja() != null ? p.getPrecioCaja().doubleValue() / upk : 0);
            row.put("valorInventario", valor);
            resultado.add(row);
        }
        return resultado;
    }

    // ─── REPORTE DE GANANCIAS ────────────────────────────────────
    /**
     * Calcula: ingresos brutos, costo total (precio proveedor), ganancia neta,
     * IVA e IBUA recaudados. Opcionalmente desglosa por producto.
     */
    public Map<String, Object> reporteGanancias(LocalDate desde, LocalDate hasta,
                                                 Long vendedorId, boolean detallePorProducto) {
        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        List<Remision> remisiones = remisionRepository.findByFechaBetween(desde, hasta)
            .stream()
            .filter(r -> r.getEstado() != EstadoRemision.ANULADA)
            .filter(r -> vendedorId == null ||
                (r.getVendedor() != null && r.getVendedor().getId().equals(vendedorId)))
            .collect(Collectors.toList());

        BigDecimal ingresoTotal   = BigDecimal.ZERO;
        BigDecimal costoTotal     = BigDecimal.ZERO;
        BigDecimal ivaTotal       = BigDecimal.ZERO;
        BigDecimal ibuaTotal      = BigDecimal.ZERO;

        // mapa producto → {cantidadVendida, ingreso, costo, ganancia}
        Map<String, Map<String, Object>> porProducto = new LinkedHashMap<>();

        for (Remision rem : remisiones) {
            ivaTotal  = ivaTotal.add(rem.getTotalIva()  != null ? rem.getTotalIva()  : BigDecimal.ZERO);
            ibuaTotal = ibuaTotal.add(rem.getTotalIbua() != null ? rem.getTotalIbua() : BigDecimal.ZERO);

            for (RemisionDetalle det : rem.getDetalles()) {
                BigDecimal ing  = det.getSubtotal();      // venta sin impuestos
                BigDecimal cost = det.getCostoTotal();    // costo proveedor
                ingresoTotal = ingresoTotal.add(ing);
                costoTotal   = costoTotal.add(cost);

                if (detallePorProducto) {
                    String key = det.getDescripcionProducto();
                    int upk = det.getProducto() != null && det.getProducto().getUnidadesPorCaja() > 0
                        ? det.getProducto().getUnidadesPorCaja() : 1;
                    Map<String, Object> p = porProducto.computeIfAbsent(key, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("producto", key);
                        m.put("unidadesPorCaja", upk);
                        m.put("cantidadVendida", BigDecimal.ZERO);
                        m.put("ingreso", BigDecimal.ZERO);
                        m.put("costo", BigDecimal.ZERO);
                        m.put("ganancia", BigDecimal.ZERO);
                        return m;
                    });
                    p.put("cantidadVendida",
                        ((BigDecimal) p.get("cantidadVendida")).add(det.getCantidad()));
                    p.put("ingreso",
                        ((BigDecimal) p.get("ingreso")).add(ing));
                    p.put("costo",
                        ((BigDecimal) p.get("costo")).add(cost));
                    p.put("ganancia",
                        ((BigDecimal) p.get("ingreso")).subtract((BigDecimal) p.get("costo")));
                }
            }
        }

        BigDecimal gananciaTotal = ingresoTotal.subtract(costoTotal);
        double margen = ingresoTotal.compareTo(BigDecimal.ZERO) > 0
            ? gananciaTotal.divide(ingresoTotal, 4, java.math.RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100)).doubleValue()
            : 0.0;

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("desde", desde.toString());
        resultado.put("hasta", hasta.toString());
        resultado.put("totalRemisiones", remisiones.size());
        resultado.put("ingresoTotal",  ingresoTotal);   // lo que pagaron los clientes (sin impuestos)
        resultado.put("costoTotal",    costoTotal);     // lo que costó a proveedor
        resultado.put("gananciaTotal", gananciaTotal);  // ingreso - costo
        resultado.put("margenPorcentaje", Math.round(margen * 100.0) / 100.0);
        resultado.put("ivaTotal",  ivaTotal);
        resultado.put("ibuaTotal", ibuaTotal);
        resultado.put("totalConImpuestos",
            ingresoTotal.add(ivaTotal).add(ibuaTotal));  // total facturado real

        if (detallePorProducto) {
            // Ordenar por ganancia descendente
            List<Map<String, Object>> lista = new ArrayList<>(porProducto.values());
            lista.sort((a, b) -> ((BigDecimal) b.get("ganancia"))
                .compareTo((BigDecimal) a.get("ganancia")));
            resultado.put("detallePorProducto", lista);
        }

        return resultado;
    }

    // ─── REPORTE KARDEX (movimientos por producto) ───────────────
    /**
     * Para cada producto activo calcula en el período:
     *   stockInicial  = stockActual - netMovimientoPeriodo
     *   entradas      = SUM(COMPRA + AJUSTE_POS)          en el período
     *   ventas        = ABS(SUM(CARGUE)) - SUM(DEVOLUCION) en el período
     *   stockFinal    = stockActual (real en BD)
     * Todo expresado en cajas (enteras) y unidades sueltas.
     */
    public List<Map<String, Object>> reporteKardex(LocalDate desde, LocalDate hasta) {
        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        List<Producto> productos = productoRepository.findByActivoTrue();

        // Traer todos los movimientos del período agrupados [productoId, tipo, suma]
        List<Object[]> movs = movimientoRepository.sumPorProductoYTipo(desde, hasta);

        // Construir mapa: productoId → { tipo → suma }
        Map<Long, Map<String, Long>> movMap = new HashMap<>();
        for (Object[] row : movs) {
            Long pid   = ((Number) row[0]).longValue();
            String tipo = row[1].toString();
            Long suma  = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            movMap.computeIfAbsent(pid, k -> new HashMap<>()).put(tipo, suma);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();

        for (Producto prod : productos) {
            int upk = prod.getUnidadesPorCaja() > 0 ? prod.getUnidadesPorCaja() : 1;
            int stockFinalUds = prod.getStockActual() != null ? prod.getStockActual() : 0;

            Map<String, Long> tipoMap = movMap.getOrDefault(prod.getId(), Collections.emptyMap());

            // Entradas = COMPRA + AJUSTE_POS (positivos por convención)
            long entradaUds = tipoMap.getOrDefault(TipoMovimiento.COMPRA.name(), 0L)
                            + tipoMap.getOrDefault(TipoMovimiento.AJUSTE_POS.name(), 0L);

            // Salidas brutas a vendedor = ABS(CARGUE) — almacenado negativo
            long cargueUds = Math.abs(tipoMap.getOrDefault(TipoMovimiento.CARGUE.name(), 0L));

            // Devoluciones que volvieron a bodega (positivos)
            long devUds = tipoMap.getOrDefault(TipoMovimiento.DEVOLUCION.name(), 0L);

            // Ajustes negativos y ventas directas (almacenados negativos)
            long ajusteNegUds = Math.abs(tipoMap.getOrDefault(TipoMovimiento.AJUSTE_NEG.name(), 0L))
                              + Math.abs(tipoMap.getOrDefault(TipoMovimiento.VENTA_DIRECTA.name(), 0L));

            // Neto del período (entradas - salidas brutas + devoluciones - ajustes neg)
            long netoPeriodo = entradaUds - cargueUds + devUds - ajusteNegUds;

            // Stock inicial = lo que había ANTES de este período
            long stockInicialUds = stockFinalUds - netoPeriodo;

            // Ventas netas al mercado = cargues que NO volvieron como devolución
            long ventasUds = cargueUds - devUds + ajusteNegUds;
            if (ventasUds < 0) ventasUds = 0;

            // Convertir a cajas y sueltas
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("idProducto",       prod.getId());
            row.put("producto",         prod.getNombreCompleto());
            row.put("unidadesPorCaja",  upk);

            row.put("stockInicialCajas",   stockInicialUds / upk);
            row.put("stockInicialSueltas", stockInicialUds % upk);

            row.put("entradasCajas",       entradaUds / upk);
            row.put("entradasSueltas",     entradaUds % upk);

            row.put("ventasCajas",         ventasUds / upk);
            row.put("ventasSueltas",       ventasUds % upk);

            row.put("devolucionesCajas",   devUds / upk);
            row.put("devolucionesSueltas", devUds % upk);

            row.put("stockFinalCajas",     stockFinalUds / upk);
            row.put("stockFinalSueltas",   stockFinalUds % upk);

            // Verificación: stockInicial + entradas - ventas debe = stockFinal
            row.put("cuadra", stockInicialUds + entradaUds - ventasUds == stockFinalUds);

            resultado.add(row);
        }

        // Ordenar por nombre de producto
        resultado.sort((a, b) -> a.get("producto").toString()
                .compareToIgnoreCase(b.get("producto").toString()));

        return resultado;
    }

    // ─── RELACIÓN DE VENTAS (para el contador / PUC) ─────────────
    /**
     * Genera la relación contable de ventas por rango de fechas.
     * Agrupa por cuenta PUC para que el contador pueda importarlo a Siigo.
     */
    public Map<String, Object> relacionVentas(LocalDate desde, LocalDate hasta) {
        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        final LocalDate f1 = desde, f2 = hasta;

        // ── Remisiones del período ────────────────────────────────
        List<Remision> remisiones = remisionRepository.findByFechaBetween(f1, f2).stream()
            .filter(r -> r.getEstado() != EstadoRemision.ANULADA)
            .collect(Collectors.toList());

        BigDecimal ventasContado  = BigDecimal.ZERO;
        BigDecimal ventasCredito  = BigDecimal.ZERO;
        BigDecimal totalIva       = BigDecimal.ZERO;
        BigDecimal totalIbua      = BigDecimal.ZERO;
        BigDecimal totalGeneral   = BigDecimal.ZERO;

        List<Map<String, Object>> filas = new ArrayList<>();

        for (Remision r : remisiones) {
            BigDecimal subtotal = r.getSubtotal() != null ? r.getSubtotal() : BigDecimal.ZERO;
            BigDecimal iva      = r.getTotalIva()  != null ? r.getTotalIva()  : BigDecimal.ZERO;
            BigDecimal ibua     = r.getTotalIbua() != null ? r.getTotalIbua() : BigDecimal.ZERO;
            BigDecimal total    = r.getTotal()     != null ? r.getTotal()     : BigDecimal.ZERO;

            totalIva   = totalIva.add(iva);
            totalIbua  = totalIbua.add(ibua);
            totalGeneral = totalGeneral.add(total);

            boolean esCredito = r.getFormaPago() == FormaPago.CREDITO ||
                                r.getFormaPago() == FormaPago.CHEQUE;
            if (esCredito) ventasCredito = ventasCredito.add(subtotal);
            else           ventasContado = ventasContado.add(subtotal);

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("fecha",      r.getFecha());
            fila.put("numero",     r.getNumero());
            fila.put("facturaDian",r.getNumeroFacturaDian());
            fila.put("cliente",    r.getCliente() != null ? r.getCliente().getRazonSocial() : "-");
            fila.put("nit",        r.getCliente() != null ? r.getCliente().getNit() : "-");
            fila.put("formaPago",  r.getFormaPago());
            fila.put("subtotal",   subtotal);
            fila.put("iva",        iva);
            fila.put("ibua",       ibua);
            fila.put("total",      total);
            fila.put("cuentaVenta", esCredito
                ? contableService.cuentaPuc("VENTA_CREDITO")
                : contableService.cuentaPuc("VENTA_CONTADO"));
            fila.put("cuentaIva",  contableService.cuentaPuc("IVA_19"));
            filas.add(fila);
        }

        // ── Notas crédito emitidas en el período ──────────────────
        List<NotaCredito> notasNC = notaCreditoRepository.findAll().stream()
            .filter(n -> !n.getFecha().isBefore(f1) && !n.getFecha().isAfter(f2))
            .filter(n -> n.getEstado() == NotaCredito.EstadoNota.EMITIDA)
            .collect(Collectors.toList());

        BigDecimal totalNotasNC = notasNC.stream()
            .map(NotaCredito::getValorNota).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Notas débito emitidas en el período ───────────────────
        List<NotaDebito> notasND = notaDebitoRepository.findAll().stream()
            .filter(n -> !n.getFecha().isBefore(f1) && !n.getFecha().isAfter(f2))
            .filter(n -> n.getEstado() == NotaDebito.EstadoNota.EMITIDA)
            .collect(Collectors.toList());

        BigDecimal totalNotasND = notasND.stream()
            .map(NotaDebito::getValorNota).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Resumen por cuentas PUC ───────────────────────────────
        List<Map<String, Object>> resumenPuc = new ArrayList<>();
        resumenPuc.add(pucRow("4135", "Ingresos ventas contado",  ventasContado, "INGRESOS"));
        resumenPuc.add(pucRow("4135", "Ingresos ventas crédito",  ventasCredito, "INGRESOS"));
        resumenPuc.add(pucRow("2408", "IVA generado",              totalIva,     "PASIVO"));
        resumenPuc.add(pucRow("2408", "IBUA generado",             totalIbua,    "PASIVO"));
        if (totalNotasNC.compareTo(BigDecimal.ZERO) > 0)
            resumenPuc.add(pucRow("4175", "Devoluciones (notas crédito)", totalNotasNC.negate(), "INGRESOS"));
        if (totalNotasND.compareTo(BigDecimal.ZERO) > 0)
            resumenPuc.add(pucRow("4210", "Cargos adicionales (notas débito)", totalNotasND, "INGRESOS"));

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("desde",        desde.toString());
        resultado.put("hasta",        hasta.toString());
        resultado.put("totalRemisiones", remisiones.size());
        resultado.put("ventasContado",  ventasContado);
        resultado.put("ventasCredito",  ventasCredito);
        resultado.put("totalIva",       totalIva);
        resultado.put("totalIbua",      totalIbua);
        resultado.put("totalNotasNC",   totalNotasNC);
        resultado.put("totalNotasND",   totalNotasND);
        resultado.put("totalGeneral",   totalGeneral.add(totalNotasND).subtract(totalNotasNC));
        resultado.put("resumenPuc",     resumenPuc);
        resultado.put("filas",          filas);
        return resultado;
    }

    private Map<String, Object> pucRow(String cuenta, String nombre, BigDecimal valor, String grupo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cuentaPuc", cuenta);
        row.put("nombre",    nombre);
        row.put("valor",     valor);
        row.put("grupo",     grupo);
        return row;
    }

    // ─── REPORTE DE NULAS (remisiones anuladas) ──────────────────
    /**
     * Devuelve, en el período indicado:
     *   - totalAnuladas:       número de remisiones con estado ANULADA
     *   - totalValorAnulado:   suma del total de esas remisiones
     *   - desglosePorProducto: por cada producto → unidades, valor y en cuántas remisiones apareció
     *   - listado:             fila por cada remisión anulada
     */
    public Map<String, Object> reporteNulas(LocalDate desde, LocalDate hasta) {
        if (desde == null) desde = LocalDate.now().withDayOfMonth(1);
        if (hasta == null) hasta = LocalDate.now();

        List<Remision> anuladas = remisionRepository
            .findByEstadoAndFechaBetween(EstadoRemision.ANULADA, desde, hasta);

        BigDecimal totalValorAnulado = BigDecimal.ZERO;

        // clave: nombre del producto
        Map<String, Map<String, Object>> porProducto = new LinkedHashMap<>();

        List<Map<String, Object>> listado = new ArrayList<>();

        for (Remision rem : anuladas) {
            BigDecimal total    = rem.getTotal()     != null ? rem.getTotal()     : BigDecimal.ZERO;
            BigDecimal subtotal = rem.getSubtotal()  != null ? rem.getSubtotal()  : BigDecimal.ZERO;
            BigDecimal iva      = rem.getTotalIva()  != null ? rem.getTotalIva()  : BigDecimal.ZERO;
            totalValorAnulado   = totalValorAnulado.add(total);

            // Fila del listado detallado
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id",       rem.getId());
            fila.put("numero",   rem.getNumero());
            fila.put("fecha",    rem.getFecha());
            fila.put("cliente",  rem.getCliente() != null ? rem.getCliente().getRazonSocial() : "-");
            fila.put("subtotal", subtotal);
            fila.put("totalIva", iva);
            fila.put("total",    total);
            listado.add(fila);

            // Desglose por producto
            for (RemisionDetalle det : rem.getDetalles()) {
                String key = det.getDescripcionProducto();
                int upk = det.getProducto() != null && det.getProducto().getUnidadesPorCaja() > 0
                    ? det.getProducto().getUnidadesPorCaja() : 1;

                Map<String, Object> p = porProducto.computeIfAbsent(key, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("producto",           key);
                    m.put("unidadesPorCaja",    upk);
                    m.put("unidadesAnuladas",   BigDecimal.ZERO);
                    m.put("valorAnulado",       BigDecimal.ZERO);
                    m.put("cantidadRemisiones", 0);
                    return m;
                });

                p.put("unidadesAnuladas",
                    ((BigDecimal) p.get("unidadesAnuladas")).add(det.getCantidad()));
                p.put("valorAnulado",
                    ((BigDecimal) p.get("valorAnulado")).add(det.getSubtotal()));
                p.put("cantidadRemisiones",
                    (Integer) p.get("cantidadRemisiones") + 1);
            }
        }

        // Ordenar por valor anulado de mayor a menor
        List<Map<String, Object>> desglose = new ArrayList<>(porProducto.values());
        desglose.sort((a, b) ->
            ((BigDecimal) b.get("valorAnulado")).compareTo((BigDecimal) a.get("valorAnulado")));

        // Ordenar listado por fecha descendente
        listado.sort((a, b) -> {
            java.time.LocalDate fa = (java.time.LocalDate) a.get("fecha");
            java.time.LocalDate fb = (java.time.LocalDate) b.get("fecha");
            return fb.compareTo(fa);
        });

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("desde",             desde.toString());
        resultado.put("hasta",             hasta.toString());
        resultado.put("totalAnuladas",     anuladas.size());
        resultado.put("totalValorAnulado", totalValorAnulado);
        resultado.put("desglosePorProducto", desglose);
        resultado.put("listado",           listado);
        return resultado;
    }

    // Reporte de devoluciones
    public List<Map<String, Object>> reporteDevoluciones(LocalDate desde, LocalDate hasta, Long vendedorId) {
        List<CargueDetalle> detalles;
        if (vendedorId != null) {
            detalles = cargueDetalleRepository.findByFechaRangoYVendedor(desde, hasta, vendedorId);
        } else {
            detalles = cargueDetalleRepository.findByFechaRango(desde, hasta);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (CargueDetalle d : detalles) {
            if (d.getCantidadDevolucion() == null || d.getCantidadDevolucion() <= 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fecha", d.getCargue().getFecha());
            row.put("vendedor", d.getCargue().getVendedor() != null ? d.getCargue().getVendedor().getNombre() : "-");
            row.put("producto", d.getProducto() != null ? d.getProducto().getNombreCompleto() : "-");
            row.put("cantidadDevuelta", d.getCantidadDevolucion());
            double precio = d.getPrecioCajaSnapshot() != null ? d.getPrecioCajaSnapshot().doubleValue() : 0;
            int upk = d.getProducto() != null && d.getProducto().getUnidadesPorCaja() > 0 ? d.getProducto().getUnidadesPorCaja() : 1;
            row.put("valorDevolucion", d.getCantidadDevolucion() * (precio / upk));
            resultado.add(row);
        }
        return resultado;
    }
}
