package com.distriasociados.inventario.service;

import com.distriasociados.inventario.dto.facturacion.LineaFacturaDto;
import com.distriasociados.inventario.dto.facturacion.NotaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.SiigoInvoiceResult;
import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Factura.EstadoFactura;
import com.distriasociados.inventario.entity.MovimientoInventario.TipoMovimiento;
import com.distriasociados.inventario.entity.NotaCredito.EstadoNota;
import com.distriasociados.inventario.entity.NotaCredito.TipoNotaCredito;
import com.distriasociados.inventario.repository.*;
import com.distriasociados.inventario.service.facturacion.SiigoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotaCreditoService {

    private final NotaCreditoRepository    notaCreditoRepo;
    private final FacturaRepository        facturaRepo;
    private final ProductoRepository       productoRepo;
    private final AbonoCreditoRepository   abonoCreditoRepo;
    private final RemisionRepository       remisionRepo;
    private final MovimientoInventarioRepository movimientoRepo;
    private final SiigoPort                siigoPort;
    private final ProductoService          productoService;

    // ─── LISTAR HISTORIAL GLOBAL ──────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<NotaCredito> listarTodas(java.time.LocalDate inicio, java.time.LocalDate fin) {
        return notaCreditoRepo.findByFechaBetweenOrderByFechaDescIdDesc(inicio, fin);
    }

    // ─── LISTAR NOTAS DE UNA FACTURA ──────────────────────────────
    public List<NotaCredito> listarPorFactura(Long facturaId) {
        return notaCreditoRepo.findByFactura_IdOrderByCreadoEnDesc(facturaId);
    }

    // ─── CREAR NOTA CRÉDITO ───────────────────────────────────────
    @Transactional
    public NotaCredito crear(Long facturaId, TipoNotaCredito tipo, String motivo,
                              List<Map<String, Object>> lineasReq, Usuario usuarioActual) {

        Factura factura = facturaRepo.findById(facturaId)
            .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + facturaId));

        if (factura.getEstado() == EstadoFactura.ANULADA) {
            throw new RuntimeException("La factura ya está anulada — no se pueden crear más notas");
        }
        if (factura.getEstado() == EstadoFactura.BORRADOR) {
            throw new RuntimeException("La factura debe estar EMITIDA ante la DIAN para crear una nota crédito");
        }

        // Generar número NC
        String numero = generarNumeroNC();

        // Construir detalles
        List<NotaCreditoDetalle> detalles = new ArrayList<>();
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (Map<String, Object> linea : lineasReq) {
            Long productoId = linea.get("productoId") != null
                ? Long.valueOf(linea.get("productoId").toString()) : null;
            Producto producto = productoId != null
                ? productoRepo.findById(productoId).orElse(null) : null;

            BigDecimal cantidad = new BigDecimal(linea.getOrDefault("cantidad", "0").toString());
            BigDecimal precioOriginal = new BigDecimal(linea.getOrDefault("precioUnitarioOriginal", "0").toString());
            BigDecimal precioNuevo = linea.get("precioUnitarioNuevo") != null
                ? new BigDecimal(linea.get("precioUnitarioNuevo").toString()) : null;
            BigDecimal pctIva = new BigDecimal(linea.getOrDefault("porcentajeIva", "0").toString());
            String descripcion = linea.getOrDefault("descripcion",
                producto != null ? producto.getNombreCompleto() : "").toString();

            BigDecimal valorLinea;
            if (tipo == TipoNotaCredito.AJUSTE_PRECIO && precioNuevo != null) {
                // Se acredita la diferencia: (precioOriginal - precioNuevo) * cantidad
                valorLinea = precioOriginal.subtract(precioNuevo).multiply(cantidad);
            } else {
                // Devolución o anulación: precio original * cantidad
                valorLinea = precioOriginal.multiply(cantidad);
            }
            if (valorLinea.compareTo(BigDecimal.ZERO) < 0) valorLinea = BigDecimal.ZERO;

            // Agregar IVA al valor de la nota
            BigDecimal ivaLinea = valorLinea.multiply(pctIva)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            valorTotal = valorTotal.add(valorLinea).add(ivaLinea);

            detalles.add(NotaCreditoDetalle.builder()
                .producto(producto)
                .descripcion(descripcion)
                .cantidad(cantidad)
                .precioUnitarioOriginal(precioOriginal)
                .precioUnitarioNuevo(precioNuevo)
                .porcentajeIva(pctIva)
                .valorLinea(valorLinea)
                .build());
        }

        NotaCredito nota = NotaCredito.builder()
            .numero(numero)
            .factura(factura)
            .tipo(tipo)
            .motivo(motivo)
            .fecha(LocalDate.now())
            .valorNota(valorTotal)
            .estado(EstadoNota.PENDIENTE)
            .creadoPor(usuarioActual)
            .build();

        // Enlazar detalles
        for (NotaCreditoDetalle d : detalles) d.setNotaCredito(nota);
        nota.getDetalles().addAll(detalles);

        nota = notaCreditoRepo.save(nota);

        // ── 1. Enviar a Siigo PRIMERO ────────────────────────────
        // Los efectos internos solo se aplican si Siigo confirma la nota.
        enviarASiigo(nota, factura);

        // ── 2. Aplicar efectos SOLO si Siigo aceptó (EMITIDA) ───
        if (nota.getEstado() == EstadoNota.EMITIDA) {
            aplicarEfectosInventario(nota, factura);
            actualizarEstadoCuenta(nota, factura);
            actualizarEstadoFactura(nota, factura);
            log.info("✅ Nota Crédito {} emitida por Siigo — efectos internos aplicados", nota.getNumero());
        } else {
            // Siigo rechazó: la nota queda como RECHAZADA, nada cambia internamente
            log.warn("⚠ Nota Crédito {} RECHAZADA por Siigo — no se aplican efectos internos. Error: {}",
                nota.getNumero(), nota.getErrorSiigo());
        }

        return nota;
    }

    // ─── HELPER: efectos en inventario ────────────────────────────
    private void aplicarEfectosInventario(NotaCredito nota, Factura factura) {
        if (nota.getTipo() != TipoNotaCredito.AJUSTE_PRECIO) {
            // Devolucion o Anulacion → stock sube
            for (NotaCreditoDetalle d : nota.getDetalles()) {
                if (d.getProducto() != null && d.getCantidad().compareTo(BigDecimal.ZERO) > 0) {
                    int uds = d.getCantidad().intValue();
                    productoService.actualizarStock(d.getProducto().getId(), uds);

                    MovimientoInventario mov = MovimientoInventario.builder()
                        .tipo(TipoMovimiento.DEVOLUCION)
                        .producto(d.getProducto())
                        .cantidad(uds)
                        .precioUnitario(d.getPrecioUnitarioOriginal())
                        .referenciaId(nota.getId())
                        .referenciaTipo("NOTA_CREDITO")
                        .observaciones("Devolución por nota crédito " + nota.getNumero()
                            + " — Factura " + factura.getNumero())
                        .usuario(nota.getCreadoPor())
                        .fecha(nota.getFecha())
                        .build();
                    movimientoRepo.save(mov);
                }
            }
        }
    }

    // ─── HELPER: efectos en estado de cuenta ──────────────────────
    private void actualizarEstadoCuenta(NotaCredito nota, Factura factura) {
        // Buscar la remisión por FK directa; si no existe, intentar por número DIAN
        Optional<com.distriasociados.inventario.entity.Remision> remisionOpt =
            remisionRepo.findFirstByFactura(factura);
        if (remisionOpt.isEmpty() && factura.getNumero() != null) {
            remisionOpt = remisionRepo.findFirstByNumeroFacturaDian(factura.getNumero());
        }
        remisionOpt.ifPresent(remision -> {
                // Solo aplica a remisiones a crédito o cheque (el formaPago está en la Remisión, no en la Factura)
                if (remision.getFormaPago() != com.distriasociados.inventario.entity.Remision.FormaPago.CREDITO &&
                    remision.getFormaPago() != com.distriasociados.inventario.entity.Remision.FormaPago.CHEQUE) return;

                // Calcular cuánto reducir: no puede superar el saldo pendiente
                BigDecimal abono = nota.getValorNota();
                BigDecimal totalAbonado = abonoCreditoRepo.sumMontoPorRemision(remision.getId());
                BigDecimal saldoPendiente = remision.getTotal().subtract(totalAbonado);
                if (abono.compareTo(saldoPendiente) > 0) abono = saldoPendiente;

                if (abono.compareTo(BigDecimal.ZERO) <= 0) return;

                String tipoDesc = switch (nota.getTipo()) {
                    case DEVOLUCION    -> "Devolución de mercancía";
                    case AJUSTE_PRECIO -> "Ajuste de precio";
                    case ANULACION     -> "Anulación de factura";
                };

                AbonoCredito abonoNC = AbonoCredito.builder()
                    .remision(remision)
                    .monto(abono)
                    .fecha(nota.getFecha())
                    .notas(nota.getNumero() + " — " + tipoDesc + ": " + nota.getMotivo())
                    .creadoPor(nota.getCreadoPor())
                    .build();
                abonoCreditoRepo.save(abonoNC);
                recalcularEstadoPago(remision);
                log.info("✅ Estado de cuenta — Remisión {} — NC {} ({}) abona: {}",
                    remision.getNumero(), nota.getNumero(), nota.getTipo(), abono);
            });
    }

    /** Recalcula y persiste el estadoPago de la remisión según los abonos actuales. */
    private void recalcularEstadoPago(com.distriasociados.inventario.entity.Remision remision) {
        BigDecimal pagado = abonoCreditoRepo.sumMontoPorRemision(remision.getId());
        com.distriasociados.inventario.entity.Remision.EstadoPago nuevo;
        if (pagado.compareTo(BigDecimal.ZERO) <= 0) {
            nuevo = com.distriasociados.inventario.entity.Remision.EstadoPago.PENDIENTE;
        } else if (pagado.compareTo(remision.getTotal()) >= 0) {
            nuevo = com.distriasociados.inventario.entity.Remision.EstadoPago.PAGADO;
        } else {
            nuevo = com.distriasociados.inventario.entity.Remision.EstadoPago.PARCIAL;
        }
        remision.setEstadoPago(nuevo);
        remisionRepo.save(remision);
    }

    // ─── HELPER: actualizar estado de la factura ──────────────────
    private void actualizarEstadoFactura(NotaCredito nota, Factura factura) {
        if (nota.getTipo() == TipoNotaCredito.ANULACION) {
            factura.setEstado(EstadoFactura.ANULADA);
        } else {
            factura.setEstado(EstadoFactura.CON_NOTA_CREDITO);
        }
        facturaRepo.save(factura);
    }

    // ─── HELPER: enviar a Siigo ────────────────────────────────────
    private void enviarASiigo(NotaCredito nota, Factura factura) {
        try {
            List<LineaFacturaDto> lineas = nota.getDetalles().stream()
                .map(d -> LineaFacturaDto.builder()
                    .codigoProducto(d.getProducto() != null ? d.getProducto().getCodigo() : "AJUSTE")
                    .descripcion(d.getDescripcion())
                    .cantidad(d.getCantidad())
                    .precioUnitario(d.getPrecioUnitarioOriginal())
                    .porcentajeIva(d.getPorcentajeIva())
                    .build())
                .collect(Collectors.toList());

            NotaSiigoRequest req = NotaSiigoRequest.builder()
                .tipoNota("credito")
                .facturaNumero(factura.getNumero())
                .facturaSiigoId(factura.getSiigoId())
                .clienteNit(factura.getCliente().getNit())
                .fecha(nota.getFecha())
                .motivo(nota.getMotivo())
                .total(nota.getValorNota())
                .lineas(lineas)
                .build();

            SiigoInvoiceResult resultado = siigoPort.crearNotaCredito(req);
            if (resultado.isExito()) {
                nota.setSiigoId(resultado.getSiigoId());
                nota.setEstado(EstadoNota.EMITIDA);
            } else {
                nota.setEstado(EstadoNota.RECHAZADA);
                nota.setErrorSiigo(resultado.getError());
                log.warn("⚠ Nota crédito {} no fue aceptada por Siigo: {}", nota.getNumero(), resultado.getError());
            }
            notaCreditoRepo.save(nota);
        } catch (Exception e) {
            nota.setEstado(EstadoNota.RECHAZADA);
            nota.setErrorSiigo(e.getMessage());
            notaCreditoRepo.save(nota);
            log.error("Error enviando nota crédito {} a Siigo: {}", nota.getNumero(), e.getMessage());
        }
    }

    // ─── HELPER: consecutivo NC ───────────────────────────────────
    private synchronized String generarNumeroNC() {
        long total = notaCreditoRepo.contarNotas() + 1;
        return String.format("NC-%04d", total);
    }
}
