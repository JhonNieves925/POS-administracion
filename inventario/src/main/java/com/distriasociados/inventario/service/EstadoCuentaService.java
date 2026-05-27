package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.AbonoCredito;
import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.entity.Remision.EstadoPago;
import com.distriasociados.inventario.entity.Remision.FormaPago;
import com.distriasociados.inventario.entity.Remision.EstadoRemision;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.repository.AbonoCreditoRepository;
import com.distriasociados.inventario.repository.NotaCreditoRepository;
import com.distriasociados.inventario.repository.NotaDebitoRepository;
import com.distriasociados.inventario.repository.RemisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EstadoCuentaService {

    private final RemisionRepository     remisionRepository;
    private final AbonoCreditoRepository abonoCreditoRepository;
    private final NotaCreditoRepository  notaCreditoRepository;
    private final NotaDebitoRepository   notaDebitoRepository;

    // ─── LISTAR CRÉDITOS ────────────────────────────────────────

    /**
     * Devuelve todos los créditos (CREDITO + CHEQUE) no anulados,
     * con su saldo pendiente, historial de abonos y días restantes.
     *
     * @param soloActivos true = solo PENDIENTE/PARCIAL; false = incluye PAGADO también
     */
    public List<Map<String, Object>> listarCreditos(boolean soloActivos) {
        List<Remision> creditos = remisionRepository.findAll().stream()
            .filter(r -> (r.getFormaPago() == FormaPago.CREDITO || r.getFormaPago() == FormaPago.CHEQUE))
            .filter(r -> r.getEstado() != EstadoRemision.ANULADA)
            .filter(r -> !soloActivos || r.getEstadoPago() != EstadoPago.PAGADO)
            .sorted(Comparator.comparing(
                r -> r.getFechaVencimientoPago() != null ? r.getFechaVencimientoPago() : LocalDate.MAX
            ))
            .toList();

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Remision r : creditos) {
            resultado.add(construirDto(r));
        }
        return resultado;
    }

    /** Construye el DTO de estado de cuenta para una remisión. */
    public Map<String, Object> construirDto(Remision r) {
        // ── Abonos manuales ──────────────────────────────────────
        BigDecimal pagadoManual = abonoCreditoRepository.sumMontoPorRemision(r.getId());

        // ── NC / ND de la factura vinculada (retroactivo: siempre correcto) ──
        BigDecimal ajusteNc = BigDecimal.ZERO;
        BigDecimal ajusteNd = BigDecimal.ZERO;
        if (r.getFactura() != null) {
            Long fid = r.getFactura().getId();
            ajusteNc = notaCreditoRepository.sumValorByFacturaId(fid);
            ajusteNd = notaDebitoRepository.sumValorByFacturaId(fid);
        }

        // ── Balance real ─────────────────────────────────────────
        // Saldo = original + cargos adicionales (ND) - créditos (NC) - abonos manuales
        BigDecimal saldo = r.getTotal()
            .add(ajusteNd)
            .subtract(ajusteNc)
            .subtract(pagadoManual);
        if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;

        // montoPagado = lo que efectivamente se ha reducido de la deuda
        BigDecimal montoPagado = pagadoManual.add(ajusteNc).subtract(ajusteNd);
        if (montoPagado.compareTo(BigDecimal.ZERO) < 0) montoPagado = BigDecimal.ZERO;

        // ── Estado de pago calculado ─────────────────────────────
        String estadoPago;
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            estadoPago = "PAGADO";
        } else if (montoPagado.compareTo(BigDecimal.ZERO) > 0 ||
                   ajusteNc.compareTo(BigDecimal.ZERO) > 0) {
            estadoPago = "PARCIAL";
        } else {
            estadoPago = "PENDIENTE";
        }

        LocalDate hoy         = LocalDate.now();
        LocalDate vencimiento = r.getFechaVencimientoPago();
        Long diasRestantes    = vencimiento != null ? ChronoUnit.DAYS.between(hoy, vencimiento) : null;

        // ── Lista de movimientos en cuenta ───────────────────────
        List<AbonoCredito> abonos = abonoCreditoRepository.findByRemision_IdOrderByFechaDesc(r.getId());
        List<Map<String, Object>> movimientos = new ArrayList<>();

        // Agregar abonos manuales
        for (AbonoCredito a : abonos) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       a.getId());
            m.put("monto",    a.getMonto());
            m.put("fecha",    a.getFecha());
            m.put("notas",    a.getNotas());
            m.put("tipo",     "ABONO");
            m.put("creadoPor", a.getCreadoPor() != null ? a.getCreadoPor().getNombre() : "-");
            movimientos.add(m);
        }

        // Agregar NC como movimientos (si hay factura vinculada)
        if (r.getFactura() != null) {
            notaCreditoRepository.findByFactura_IdOrderByCreadoEnDesc(r.getFactura().getId())
                .stream()
                .filter(n -> n.getEstado().name().equals("EMITIDA") || n.getEstado().name().equals("PENDIENTE"))
                .forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",       n.getId());
                    m.put("monto",    n.getValorNota().negate()); // negativo = reducción de deuda
                    m.put("fecha",    n.getFecha());
                    m.put("notas",    n.getNumero() + " — " + labelTipoNc(n.getTipo().name()) + ": " + n.getMotivo());
                    m.put("tipo",     "NOTA_CREDITO");
                    m.put("creadoPor", n.getCreadoPor() != null ? n.getCreadoPor().getNombre() : "-");
                    movimientos.add(m);
                });

            notaDebitoRepository.findByFactura_IdOrderByCreadoEnDesc(r.getFactura().getId())
                .stream()
                .filter(n -> n.getEstado().name().equals("EMITIDA") || n.getEstado().name().equals("PENDIENTE"))
                .forEach(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",       n.getId());
                    m.put("monto",    n.getValorNota()); // positivo = aumento de deuda
                    m.put("fecha",    n.getFecha());
                    m.put("notas",    n.getNumero() + " — " + labelTipoNd(n.getTipo().name()) + ": " + n.getMotivo());
                    m.put("tipo",     "NOTA_DEBITO");
                    m.put("creadoPor", n.getCreadoPor() != null ? n.getCreadoPor().getNombre() : "-");
                    movimientos.add(m);
                });
        }

        // Ordenar movimientos por fecha desc
        movimientos.sort((a, b) -> {
            Object fa = a.get("fecha"), fb = b.get("fecha");
            if (fa == null || fb == null) return 0;
            return fb.toString().compareTo(fa.toString());
        });

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("remisionId",     r.getId());
        dto.put("numero",         r.getNumero());
        dto.put("fecha",          r.getFecha());
        dto.put("clienteId",      r.getCliente() != null ? r.getCliente().getId() : null);
        dto.put("clienteNombre",  r.getCliente() != null ? r.getCliente().getRazonSocial() : "-");
        dto.put("clienteNit",     r.getCliente() != null ? r.getCliente().getNit() : "-");
        dto.put("clienteTel",     r.getCliente() != null ? r.getCliente().getTelefono() : "-");
        dto.put("vendedor",       r.getVendedor() != null ? r.getVendedor().getNombre() : "-");
        dto.put("formaPago",      r.getFormaPago());
        dto.put("diasCredito",    r.getDiasCredito());
        dto.put("fechaVencimiento", vencimiento);
        dto.put("diasRestantes",  diasRestantes);
        dto.put("totalVenta",     r.getTotal());
        dto.put("ajusteNc",       ajusteNc);
        dto.put("ajusteNd",       ajusteNd);
        dto.put("montoPagado",    montoPagado);
        dto.put("saldoPendiente", saldo);
        dto.put("estadoPago",     estadoPago);
        dto.put("abonos",         movimientos);
        return dto;
    }

    private String labelTipoNc(String tipo) {
        return switch (tipo) {
            case "DEVOLUCION"    -> "Devolución";
            case "AJUSTE_PRECIO" -> "Ajuste de precio";
            case "ANULACION"     -> "Anulación";
            default              -> tipo;
        };
    }

    private String labelTipoNd(String tipo) {
        return switch (tipo) {
            case "INTERESES"       -> "Intereses de mora";
            case "AJUSTE_PRECIO"   -> "Cambio de valor";
            case "CARGO_ADICIONAL" -> "Gastos por cobrar";
            default                -> tipo;
        };
    }

    // ─── REGISTRAR ABONO ────────────────────────────────────────

    @Transactional
    public Map<String, Object> registrarAbono(Long remisionId, BigDecimal monto,
                                               LocalDate fecha, String notas,
                                               Usuario usuarioActual) {
        Remision remision = remisionRepository.findById(remisionId)
            .orElseThrow(() -> new RuntimeException("Remisión no encontrada: " + remisionId));

        if (remision.getFormaPago() != FormaPago.CREDITO && remision.getFormaPago() != FormaPago.CHEQUE) {
            throw new RuntimeException("Solo se pueden registrar abonos en remisiones a crédito o cheque");
        }
        if (remision.getEstadoPago() == EstadoPago.PAGADO) {
            throw new RuntimeException("Esta remisión ya está completamente pagada");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El monto del abono debe ser mayor a cero");
        }

        AbonoCredito abono = AbonoCredito.builder()
            .remision(remision)
            .monto(monto)
            .fecha(fecha != null ? fecha : LocalDate.now())
            .notas(notas)
            .creadoPor(usuarioActual)
            .build();
        abonoCreditoRepository.save(abono);

        // Recalcular estado de pago
        actualizarEstadoPago(remision);

        log.info("Abono de {} registrado en remisión {} — saldo restante: {}",
            monto, remision.getNumero(),
            remision.getTotal().subtract(abonoCreditoRepository.sumMontoPorRemision(remisionId)));

        return construirDto(remision);
    }

    // ─── PAGAR TOTAL ────────────────────────────────────────────

    @Transactional
    public Map<String, Object> pagarTotal(Long remisionId, String notas, Usuario usuarioActual) {
        Remision remision = remisionRepository.findById(remisionId)
            .orElseThrow(() -> new RuntimeException("Remisión no encontrada: " + remisionId));

        if (remision.getEstadoPago() == EstadoPago.PAGADO) {
            throw new RuntimeException("Esta remisión ya está completamente pagada");
        }

        BigDecimal pagadoAntes = abonoCreditoRepository.sumMontoPorRemision(remisionId);
        BigDecimal saldoPendiente = remision.getTotal().subtract(pagadoAntes);

        if (saldoPendiente.compareTo(BigDecimal.ZERO) > 0) {
            // Registrar un abono por el saldo restante
            AbonoCredito abono = AbonoCredito.builder()
                .remision(remision)
                .monto(saldoPendiente)
                .fecha(LocalDate.now())
                .notas(notas != null && !notas.isBlank() ? notas : "Pago total")
                .creadoPor(usuarioActual)
                .build();
            abonoCreditoRepository.save(abono);
        }

        remision.setEstadoPago(EstadoPago.PAGADO);
        remisionRepository.save(remision);

        log.info("Remisión {} marcada como PAGADA TOTAL", remision.getNumero());
        return construirDto(remision);
    }

    // ─── ALERTAS PARA EL DASHBOARD ──────────────────────────────

    /**
     * Retorna resumen de alertas: vencidas y por vencer en los próximos N días.
     * Usado por el widget de alerta del dashboard.
     */
    public Map<String, Object> obtenerAlertas(int diasUmbral) {
        LocalDate hoy = LocalDate.now();

        List<Remision> activas = remisionRepository.findAll().stream()
            .filter(r -> (r.getFormaPago() == FormaPago.CREDITO || r.getFormaPago() == FormaPago.CHEQUE))
            .filter(r -> r.getEstado() != EstadoRemision.ANULADA)
            .filter(r -> r.getEstadoPago() != EstadoPago.PAGADO)
            .toList();

        long vencidas = activas.stream()
            .filter(r -> r.getFechaVencimientoPago() != null &&
                         r.getFechaVencimientoPago().isBefore(hoy))
            .count();

        long vencenHoy = activas.stream()
            .filter(r -> hoy.equals(r.getFechaVencimientoPago()))
            .count();

        long porVencer = activas.stream()
            .filter(r -> r.getFechaVencimientoPago() != null &&
                         !r.getFechaVencimientoPago().isBefore(hoy) &&
                         r.getFechaVencimientoPago().isBefore(hoy.plusDays(diasUmbral + 1)))
            .count();

        BigDecimal totalPendiente = activas.stream()
            .map(r -> {
                BigDecimal p = abonoCreditoRepository.sumMontoPorRemision(r.getId());
                return r.getTotal().subtract(p);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVencido = activas.stream()
            .filter(r -> r.getFechaVencimientoPago() != null &&
                         r.getFechaVencimientoPago().isBefore(hoy))
            .map(r -> {
                BigDecimal p = abonoCreditoRepository.sumMontoPorRemision(r.getId());
                return r.getTotal().subtract(p);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> alertas = new LinkedHashMap<>();
        alertas.put("totalCreditosActivos", activas.size());
        alertas.put("vencidas",       vencidas);
        alertas.put("vencenHoy",      vencenHoy);
        alertas.put("porVencer",      porVencer);   // próximos diasUmbral días
        alertas.put("totalPendiente", totalPendiente);
        alertas.put("totalVencido",   totalVencido);
        alertas.put("hayAlertas",     vencidas > 0 || vencenHoy > 0 || porVencer > 0);
        return alertas;
    }

    // ─── REPORTE ────────────────────────────────────────────────

    /**
     * Genera datos para el reporte de Estado de Cuenta por rango de fechas.
     * Incluye tanto cobros pendientes como pagados en el período.
     */
    public Map<String, Object> generarReporte(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null)    fin   = LocalDate.now();

        final LocalDate f1 = inicio, f2 = fin;

        List<Remision> creditos = remisionRepository.findAll().stream()
            .filter(r -> (r.getFormaPago() == FormaPago.CREDITO || r.getFormaPago() == FormaPago.CHEQUE))
            .filter(r -> r.getEstado() != EstadoRemision.ANULADA)
            .filter(r -> !r.getFecha().isBefore(f1) && !r.getFecha().isAfter(f2))
            .sorted(Comparator.comparing(Remision::getFecha))
            .toList();

        List<Map<String, Object>> filas = new ArrayList<>();
        BigDecimal totalVentas = BigDecimal.ZERO;
        BigDecimal totalCobrado = BigDecimal.ZERO;
        BigDecimal totalPendiente = BigDecimal.ZERO;

        for (Remision r : creditos) {
            BigDecimal pagado = abonoCreditoRepository.sumMontoPorRemision(r.getId());
            BigDecimal saldo  = r.getTotal().subtract(pagado);
            if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;

            totalVentas    = totalVentas.add(r.getTotal());
            totalCobrado   = totalCobrado.add(pagado);
            totalPendiente = totalPendiente.add(saldo);

            List<AbonoCredito> abonos = abonoCreditoRepository.findByRemision_IdOrderByFechaDesc(r.getId());

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("remisionId",    r.getId());
            fila.put("numero",        r.getNumero());
            fila.put("fecha",         r.getFecha());
            fila.put("cliente",       r.getCliente() != null ? r.getCliente().getRazonSocial() : "-");
            fila.put("nit",           r.getCliente() != null ? r.getCliente().getNit() : "-");
            fila.put("totalVenta",    r.getTotal());
            fila.put("montoPagado",   pagado);
            fila.put("saldo",         saldo);
            fila.put("estadoPago",    r.getEstadoPago());
            fila.put("vencimiento",   r.getFechaVencimientoPago());
            fila.put("abonos",        abonos.stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fecha", a.getFecha()); m.put("monto", a.getMonto()); m.put("notas", a.getNotas());
                return m;
            }).toList());
            filas.add(fila);
        }

        Map<String, Object> reporte = new LinkedHashMap<>();
        reporte.put("inicio", inicio);
        reporte.put("fin", fin);
        reporte.put("filas", filas);
        reporte.put("totalVentas", totalVentas);
        reporte.put("totalCobrado", totalCobrado);
        reporte.put("totalPendiente", totalPendiente);
        return reporte;
    }

    // ─── RESUMEN GLOBAL DE CARTERA ──────────────────────────────

    /**
     * Resumen global de cartera:
     *  - Totales: cartera total, recaudado en el período, pendiente, % cobranza
     *  - Recaudos del período (abonos registrados entre inicio y fin)
     *  - Cartera agrupada por cliente
     */
    public Map<String, Object> resumenCartera(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null)    fin   = LocalDate.now();

        final LocalDate f1 = inicio, f2 = fin;

        // ── Cartera total agrupada por cliente ───────────────────
        List<Remision> todasCreditos = remisionRepository.findAll().stream()
            .filter(r -> r.getFormaPago() == FormaPago.CREDITO || r.getFormaPago() == FormaPago.CHEQUE)
            .filter(r -> r.getEstado() != EstadoRemision.ANULADA)
            .toList();

        LocalDate hoy = LocalDate.now();

        // Agrupar por cliente
        Map<Long, Map<String, Object>> porCliente = new LinkedHashMap<>();
        BigDecimal totalCartera   = BigDecimal.ZERO;
        BigDecimal totalPendiente = BigDecimal.ZERO;
        BigDecimal totalCobrado   = BigDecimal.ZERO;

        for (Remision r : todasCreditos) {
            BigDecimal pagado = abonoCreditoRepository.sumMontoPorRemision(r.getId());
            BigDecimal ajNc   = r.getFactura() != null
                ? notaCreditoRepository.sumValorByFacturaId(r.getFactura().getId()) : BigDecimal.ZERO;
            BigDecimal ajNd   = r.getFactura() != null
                ? notaDebitoRepository.sumValorByFacturaId(r.getFactura().getId()) : BigDecimal.ZERO;
            BigDecimal saldo  = r.getTotal().add(ajNd).subtract(ajNc).subtract(pagado);
            if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;
            BigDecimal cobrado = r.getTotal().subtract(saldo);
            if (cobrado.compareTo(BigDecimal.ZERO) < 0) cobrado = BigDecimal.ZERO;

            totalCartera   = totalCartera.add(r.getTotal());
            totalPendiente = totalPendiente.add(saldo);
            totalCobrado   = totalCobrado.add(cobrado);

            if (r.getCliente() == null) continue;
            Long cid = r.getCliente().getId();

            Map<String, Object> cli = porCliente.computeIfAbsent(cid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("clienteId",     cid);
                m.put("clienteNombre", r.getCliente().getRazonSocial());
                m.put("clienteNit",    r.getCliente().getNit());
                m.put("telefono",      r.getCliente().getTelefono());
                m.put("numFacturas",   0);
                m.put("totalVendido",  BigDecimal.ZERO);
                m.put("totalCobrado",  BigDecimal.ZERO);
                m.put("saldoPendiente",BigDecimal.ZERO);
                m.put("tieneVencida",  false);
                return m;
            });

            cli.put("numFacturas", (int) cli.get("numFacturas") + 1);
            cli.put("totalVendido",   ((BigDecimal) cli.get("totalVendido")).add(r.getTotal()));
            cli.put("totalCobrado",   ((BigDecimal) cli.get("totalCobrado")).add(cobrado));
            cli.put("saldoPendiente", ((BigDecimal) cli.get("saldoPendiente")).add(saldo));

            boolean vencida = r.getFechaVencimientoPago() != null
                && r.getFechaVencimientoPago().isBefore(hoy)
                && saldo.compareTo(BigDecimal.ZERO) > 0;
            if (vencida) cli.put("tieneVencida", true);
        }

        // Ordenar por saldo pendiente descendente
        List<Map<String, Object>> listaClientes = new ArrayList<>(porCliente.values());
        listaClientes.sort((a, b) ->
            ((BigDecimal) b.get("saldoPendiente")).compareTo((BigDecimal) a.get("saldoPendiente")));

        // ── Recaudos del período (abonos reales en el rango) ─────
        List<AbonoCredito> abonosPeriodo = abonoCreditoRepository.findByFechaBetween(f1, f2)
            .stream().filter(a -> a.getMonto().compareTo(BigDecimal.ZERO) > 0).toList();

        BigDecimal recaudadoPeriodo = abonoCreditoRepository.sumRecaudadoEnPeriodo(f1, f2);

        List<Map<String, Object>> recaudos = abonosPeriodo.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       a.getId());
            m.put("fecha",    a.getFecha());
            m.put("cliente",  a.getRemision().getCliente() != null
                ? a.getRemision().getCliente().getRazonSocial() : "-");
            m.put("nit",      a.getRemision().getCliente() != null
                ? a.getRemision().getCliente().getNit() : "-");
            m.put("remision", a.getRemision().getNumero());
            m.put("monto",    a.getMonto());
            m.put("notas",    a.getNotas());
            m.put("registradoPor", a.getCreadoPor() != null ? a.getCreadoPor().getNombre() : "-");
            return m;
        }).toList();

        // ── Porcentaje de cobranza ───────────────────────────────
        double pctCobranza = totalCartera.compareTo(BigDecimal.ZERO) > 0
            ? totalCobrado.divide(totalCartera, 4, java.math.RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100)).doubleValue()
            : 0.0;

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("inicio",            f1);
        resultado.put("fin",               f2);
        resultado.put("totalCartera",      totalCartera);
        resultado.put("totalCobrado",      totalCobrado);
        resultado.put("totalPendiente",    totalPendiente);
        resultado.put("recaudadoPeriodo",  recaudadoPeriodo);
        resultado.put("pctCobranza",       Math.round(pctCobranza * 10.0) / 10.0);
        resultado.put("numClientesConDeuda", listaClientes.stream()
            .filter(c -> ((BigDecimal) c.get("saldoPendiente")).compareTo(BigDecimal.ZERO) > 0).count());
        resultado.put("porCliente",        listaClientes);
        resultado.put("recaudosPeriodo",   recaudos);
        return resultado;
    }

    // ─── HELPER INTERNO ─────────────────────────────────────────

    private void actualizarEstadoPago(Remision remision) {
        BigDecimal pagado = abonoCreditoRepository.sumMontoPorRemision(remision.getId());
        EstadoPago nuevo;
        if (pagado.compareTo(BigDecimal.ZERO) <= 0) {
            nuevo = EstadoPago.PENDIENTE;
        } else if (pagado.compareTo(remision.getTotal()) >= 0) {
            nuevo = EstadoPago.PAGADO;
        } else {
            nuevo = EstadoPago.PARCIAL;
        }
        remision.setEstadoPago(nuevo);
        remisionRepository.save(remision);
    }
}
