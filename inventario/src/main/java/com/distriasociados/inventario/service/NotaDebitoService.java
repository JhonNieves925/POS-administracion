package com.distriasociados.inventario.service;

import com.distriasociados.inventario.dto.facturacion.LineaFacturaDto;
import com.distriasociados.inventario.dto.facturacion.NotaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.SiigoInvoiceResult;
import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Factura.EstadoFactura;
import com.distriasociados.inventario.entity.NotaDebito.EstadoNota;
import com.distriasociados.inventario.entity.NotaDebito.TipoNotaDebito;
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
public class NotaDebitoService {

    private final NotaDebitoRepository  notaDebitoRepo;
    private final FacturaRepository     facturaRepo;
    private final ProductoRepository    productoRepo;
    private final RemisionRepository    remisionRepo;
    private final AbonoCreditoRepository abonoCreditoRepo;
    private final SiigoPort             siigoPort;

    // ─── LISTAR HISTORIAL GLOBAL ──────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<NotaDebito> listarTodas(java.time.LocalDate inicio, java.time.LocalDate fin) {
        return notaDebitoRepo.findByFechaBetweenOrderByFechaDescIdDesc(inicio, fin);
    }

    // ─── LISTAR POR FACTURA ───────────────────────────────────────
    public List<NotaDebito> listarPorFactura(Long facturaId) {
        return notaDebitoRepo.findByFactura_IdOrderByCreadoEnDesc(facturaId);
    }

    // ─── CREAR ─────────────────────────────────────────────────────
    @Transactional
    public NotaDebito crear(Long facturaId, TipoNotaDebito tipo, String motivo,
                             List<Map<String, Object>> lineasReq, Usuario usuarioActual) {

        Factura factura = facturaRepo.findById(facturaId)
            .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + facturaId));

        if (factura.getEstado() == EstadoFactura.ANULADA) {
            throw new RuntimeException("La factura está anulada — no se pueden crear notas débito");
        }
        if (factura.getEstado() == EstadoFactura.BORRADOR) {
            throw new RuntimeException("La factura debe estar EMITIDA para crear una nota débito");
        }

        String numero = generarNumeroND();

        List<NotaDebitoDetalle> detalles = new ArrayList<>();
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (Map<String, Object> linea : lineasReq) {
            Long productoId = linea.get("productoId") != null
                ? Long.valueOf(linea.get("productoId").toString()) : null;
            Producto producto = productoId != null
                ? productoRepo.findById(productoId).orElse(null) : null;

            BigDecimal cantidad     = new BigDecimal(linea.getOrDefault("cantidad", "1").toString());
            BigDecimal precio       = new BigDecimal(linea.getOrDefault("precioUnitario", "0").toString());
            BigDecimal pctIva       = new BigDecimal(linea.getOrDefault("porcentajeIva", "0").toString());
            String descripcion      = linea.getOrDefault("descripcion",
                producto != null ? producto.getNombreCompleto() : "Cargo adicional").toString();

            BigDecimal valorLinea = precio.multiply(cantidad);
            BigDecimal ivaLinea   = valorLinea.multiply(pctIva)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            valorTotal = valorTotal.add(valorLinea).add(ivaLinea);

            detalles.add(NotaDebitoDetalle.builder()
                .producto(producto)
                .descripcion(descripcion)
                .cantidad(cantidad)
                .precioUnitario(precio)
                .porcentajeIva(pctIva)
                .valorLinea(valorLinea)
                .build());
        }

        NotaDebito nota = NotaDebito.builder()
            .numero(numero)
            .factura(factura)
            .tipo(tipo)
            .motivo(motivo)
            .fecha(LocalDate.now())
            .valorNota(valorTotal)
            .estado(EstadoNota.PENDIENTE)
            .creadoPor(usuarioActual)
            .build();

        for (NotaDebitoDetalle d : detalles) d.setNotaDebito(nota);
        nota.getDetalles().addAll(detalles);
        nota = notaDebitoRepo.save(nota);

        // ── 1. Enviar a Siigo PRIMERO ────────────────────────────
        // Los efectos internos solo se aplican si Siigo confirma la nota.
        enviarASiigo(nota, factura);

        // ── 2. Aplicar efectos SOLO si Siigo aceptó (EMITIDA) ───
        if (nota.getEstado() == EstadoNota.EMITIDA) {
            actualizarEstadoCuentaDebito(nota, factura);
            factura.setEstado(EstadoFactura.CON_NOTA_DEBITO);
            facturaRepo.save(factura);
            log.info("✅ Nota Débito {} emitida por Siigo — efectos internos aplicados", nota.getNumero());
        } else {
            log.warn("⚠ Nota Débito {} RECHAZADA por Siigo — no se aplican efectos internos. Error: {}",
                nota.getNumero(), nota.getErrorSiigo());
        }

        return nota;
    }

    // ─── HELPER: estado de cuenta — la ND aumenta lo que debe ─────
    private void actualizarEstadoCuentaDebito(NotaDebito nota, Factura factura) {
        // Buscar la remisión por FK directa; si no existe, intentar por número DIAN
        Optional<com.distriasociados.inventario.entity.Remision> remisionOpt =
            remisionRepo.findFirstByFactura(factura);
        if (remisionOpt.isEmpty() && factura.getNumero() != null) {
            remisionOpt = remisionRepo.findFirstByNumeroFacturaDian(factura.getNumero());
        }
        remisionOpt.ifPresent(remision -> {
                // Solo aplica a remisiones a crédito o cheque
                if (remision.getFormaPago() != com.distriasociados.inventario.entity.Remision.FormaPago.CREDITO &&
                    remision.getFormaPago() != com.distriasociados.inventario.entity.Remision.FormaPago.CHEQUE) return;

                // La ND sube la deuda: se registra como un abono NEGATIVO.
                // Fórmula de saldo = total - sum(abonos).
                // Un abono negativo reduce la suma, por lo que el saldo (deuda) aumenta.
                BigDecimal cargoNegativo = nota.getValorNota().negate();

                String tipoDesc = switch (nota.getTipo()) {
                    case INTERESES       -> "Intereses de mora";
                    case AJUSTE_PRECIO   -> "Cambio de valor";
                    case CARGO_ADICIONAL -> "Gastos por cobrar";
                };

                AbonoCredito cargo = AbonoCredito.builder()
                    .remision(remision)
                    .monto(cargoNegativo)
                    .fecha(nota.getFecha())
                    .notas(nota.getNumero() + " — " + tipoDesc + ": " + nota.getMotivo())
                    .creadoPor(nota.getCreadoPor())
                    .build();
                abonoCreditoRepo.save(cargo);
                recalcularEstadoPago(remision);
                log.info("✅ Estado de cuenta — Remisión {} — ND {} ({}) cargo: {}",
                    remision.getNumero(), nota.getNumero(), nota.getTipo(), nota.getValorNota());
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

    // ─── HELPER: enviar a Siigo ────────────────────────────────────
    private void enviarASiigo(NotaDebito nota, Factura factura) {
        try {
            List<LineaFacturaDto> lineas = nota.getDetalles().stream()
                .map(d -> LineaFacturaDto.builder()
                    .codigoProducto(d.getProducto() != null ? d.getProducto().getCodigo() : "CARGO")
                    .descripcion(d.getDescripcion())
                    .cantidad(d.getCantidad())
                    .precioUnitario(d.getPrecioUnitario())
                    .porcentajeIva(d.getPorcentajeIva())
                    .build())
                .collect(Collectors.toList());

            NotaSiigoRequest req = NotaSiigoRequest.builder()
                .tipoNota("debito")
                .facturaNumero(factura.getNumero())
                .facturaSiigoId(factura.getSiigoId())
                .clienteNit(factura.getCliente().getNit())
                .fecha(nota.getFecha())
                .motivo(nota.getMotivo())
                .total(nota.getValorNota())
                .lineas(lineas)
                .build();

            SiigoInvoiceResult resultado = siigoPort.crearNotaDebito(req);
            if (resultado.isExito()) {
                nota.setSiigoId(resultado.getSiigoId());
                nota.setEstado(EstadoNota.EMITIDA);
            } else {
                nota.setEstado(EstadoNota.RECHAZADA);
                nota.setErrorSiigo(resultado.getError());
            }
            notaDebitoRepo.save(nota);
        } catch (Exception e) {
            nota.setEstado(EstadoNota.RECHAZADA);
            nota.setErrorSiigo(e.getMessage());
            notaDebitoRepo.save(nota);
            log.error("Error enviando nota débito {} a Siigo: {}", nota.getNumero(), e.getMessage());
        }
    }

    private synchronized String generarNumeroND() {
        long total = notaDebitoRepo.contarNotas() + 1;
        return String.format("ND-%04d", total);
    }
}
