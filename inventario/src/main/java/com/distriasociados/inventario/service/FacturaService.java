package com.distriasociados.inventario.service;

import com.distriasociados.inventario.config.SiigoClientService;
import com.distriasociados.inventario.dto.facturacion.FacturaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.LineaFacturaDto;
import com.distriasociados.inventario.dto.facturacion.SiigoInvoiceResult;
import com.distriasociados.inventario.dto.request.CrearFacturaDetalleRequest;
import com.distriasociados.inventario.dto.request.CrearFacturaManualRequest;
import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Factura.*;
import com.distriasociados.inventario.exception.NegocioException;
import com.distriasociados.inventario.repository.*;
import com.distriasociados.inventario.service.facturacion.SiigoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaService {

    private final FacturaRepository facturaRepository;
    private final RemisionRepository remisionRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;

    /** Siempre disponible: MockSiigoService (dev) o RealSiigoService (prod) */
    private final SiigoPort siigoPort;

    /** Solo disponible cuando facturacion.proveedor=siigo — para anular en DIAN */
    @Autowired(required = false)
    private SiigoClientService siigoClient;

    @Value("${factura.prefijo}")
    private String prefijo;


    // ─── CONSULTAS ───────────────────────────────────────────

    public List<Factura> listarPorFecha(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null) fin = LocalDate.now();
        return facturaRepository.findByFechaBetween(inicio, fin);
    }

    public Factura buscarPorId(Long id) {
        return facturaRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Factura no encontrada"));
    }

    public Factura buscarPorNumero(String numero) {
        return facturaRepository.findByNumero(numero)
                .orElseThrow(() -> new NegocioException("Factura no encontrada"));
    }


    // ─── CREAR FACTURA DESDE REMISIÓN ────────────────────────

    @Transactional
    public Factura crearDesdeRemision(Long remisionId, Usuario usuarioActual) {
        Remision remision = remisionRepository.findById(remisionId)
                .orElseThrow(() -> new NegocioException("Remisión no encontrada"));

        if (remision.getEstado() == Remision.EstadoRemision.FACTURADA) {
            throw new NegocioException("Esta remisión ya fue facturada");
        }

        Factura factura = Factura.builder()
                .numero(generarNumero())
                .cliente(remision.getCliente())
                .fecha(LocalDate.now())
                .fechaVencimiento(LocalDate.now())
                .formaPago(FormaPago.CONTADO)
                .medioPago(MedioPago.EFECTIVO)
                .subtotal(remision.getSubtotal())
                .totalIva(remision.getTotalIva())
                .total(remision.getTotal())
                .descuento(BigDecimal.ZERO)
                .estado(EstadoFactura.BORRADOR)
                .creadoPor(usuarioActual)
                .build();

        List<FacturaDetalle> detalles = new ArrayList<>();
        for (RemisionDetalle rd : remision.getDetalles()) {
            FacturaDetalle fd = FacturaDetalle.builder()
                    .factura(factura)
                    .producto(rd.getProducto())
                    .descripcion(rd.getDescripcionProducto())
                    .cantidad(rd.getCantidad())
                    .precioUnitario(rd.getPrecioUnitario())
                    .descuento(BigDecimal.ZERO)
                    .porcentajeIva(rd.getPorcentajeIva())
                    .build();
            detalles.add(fd);
        }
        factura.setDetalles(detalles);

        Factura guardada = facturaRepository.save(factura);
        remision.setEstado(Remision.EstadoRemision.FACTURADA);
        remision.setFactura(guardada);
        remisionRepository.save(remision);

        return guardada;
    }


    // ─── CREAR FACTURA MANUAL DESDE DTO DEL FRONTEND ──────────

    @Transactional
    public Factura crearManualDesdeRequest(CrearFacturaManualRequest request, Usuario usuarioActual) {
        Cliente cliente = clienteRepository.findById(request.getClienteId())
            .orElseThrow(() -> new NegocioException("Cliente no encontrado"));

        Factura factura = new Factura();
        factura.setCliente(cliente);
        factura.setMedioPago(MedioPago.EFECTIVO);
        factura.setDescuento(BigDecimal.ZERO);

        // Forma de pago
        String fpStr = request.getFormaPago() != null ? request.getFormaPago() : "CONTADO";
        try {
            factura.setFormaPago(FormaPago.valueOf(fpStr));
        } catch (IllegalArgumentException e) {
            factura.setFormaPago(FormaPago.CONTADO);
        }

        // Fecha de vencimiento: hoy para contado, hoy + diasCredito para crédito
        if (factura.getFormaPago() == FormaPago.CREDITO
                && request.getDiasCredito() != null && request.getDiasCredito() > 0) {
            factura.setFechaVencimiento(LocalDate.now().plusDays(request.getDiasCredito()));
        } else {
            factura.setFechaVencimiento(LocalDate.now());
        }

        String obs = request.getObservaciones();
        factura.setObservaciones(obs != null && !obs.isBlank() ? obs.trim() : null);

        List<FacturaDetalle> detalles = new ArrayList<>();
        for (CrearFacturaDetalleRequest d : request.getDetalles()) {
            Producto prod = productoRepository.findById(d.getProductoId())
                .orElseThrow(() -> new NegocioException("Producto no encontrado"));

            FacturaDetalle det = new FacturaDetalle();
            det.setFactura(factura);
            det.setProducto(prod);
            det.setDescripcion(prod.getNombreCompleto());
            det.setCantidad(new BigDecimal(d.getCantidad()));
            det.setPrecioUnitario(d.getPrecioUnitario());
            det.setDescuento(BigDecimal.ZERO);
            det.setPorcentajeIva(BigDecimal.valueOf(prod.getPorcentajeIva()));
            detalles.add(det);
        }

        factura.setDetalles(detalles);
        return crearManual(factura, usuarioActual);
    }


    // ─── CREAR FACTURA MANUAL ─────────────────────────────────

    @Transactional
    public Factura crearManual(Factura factura, Usuario usuarioActual) {
        factura.setNumero(generarNumero());
        factura.setFecha(LocalDate.now());
        factura.setEstado(EstadoFactura.BORRADOR);
        factura.setCreadoPor(usuarioActual);

        BigDecimal subtotal = factura.getDetalles().stream()
                .map(FacturaDetalle::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIva = factura.getDetalles().stream()
                .map(FacturaDetalle::getValorIva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        factura.setSubtotal(subtotal);
        factura.setTotalIva(totalIva);
        factura.setTotal(subtotal.add(totalIva)
                .subtract(factura.getDescuento() != null ?
                    factura.getDescuento() : BigDecimal.ZERO));

        for (FacturaDetalle d : factura.getDetalles()) {
            d.setFactura(factura);
        }

        return facturaRepository.save(factura);
    }


    // ─── EMITIR A SIIGO (DIAN) ───────────────────────────────

    @Transactional
    public Factura emitirSiigo(Long facturaId) {
        Factura factura = buscarPorId(facturaId);

        if (factura.getEstado() == EstadoFactura.EMITIDA) {
            throw new NegocioException("La factura ya fue emitida");
        }

        Cliente cliente = factura.getCliente();
        LocalDate fechaVenc = factura.getFechaVencimiento() != null
            ? factura.getFechaVencimiento() : factura.getFecha();

        List<LineaFacturaDto> lineas = factura.getDetalles().stream()
            .map(d -> LineaFacturaDto.builder()
                .codigoProducto(d.getProducto().getSiigoCodigo() != null
                    ? d.getProducto().getSiigoCodigo() : d.getProducto().getCodigo())
                .descripcion(d.getDescripcion())
                .cantidad(d.getCantidad())
                .precioUnitario(d.getPrecioUnitario())
                .porcentajeIva(d.getPorcentajeIva())
                .build())
            .collect(Collectors.toList());

        FacturaSiigoRequest siigoReq = FacturaSiigoRequest.builder()
            .clienteNit(cliente.getNit())
            .clienteNombre(cliente.getRazonSocial())
            .clienteTipoPersona(cliente.getTipoPersona() == Cliente.TipoPersona.NATURAL
                ? "NATURAL" : "JURIDICA")
            .fecha(factura.getFecha())
            .fechaVencimiento(fechaVenc)
            .formaPago(factura.getFormaPago() != null ? factura.getFormaPago().name() : "CONTADO")
            .lineas(lineas)
            .totalSinIva(factura.getSubtotal())
            .totalIva(factura.getTotalIva())
            .total(factura.getTotal())
            .remisionIds(List.of())
            .esConsolidadaConsumidorFinal(false)
            .build();

        SiigoInvoiceResult resultado = siigoPort.crearFactura(siigoReq);

        if (!resultado.isExito()) {
            log.error("Siigo rechazó factura {}: {}", factura.getNumero(), resultado.getError());
            throw new NegocioException(
                "Error al emitir la factura en la DIAN. Verifique los datos e intente nuevamente.");
        }

        factura.setSiigoId(resultado.getSiigoId());
        if (resultado.getCufe() != null) factura.setCufe(resultado.getCufe());
        factura.setEstado(EstadoFactura.EMITIDA);
        log.info("Factura {} emitida en DIAN — Siigo ID: {}, Número DIAN: {}",
            factura.getNumero(), resultado.getSiigoId(), resultado.getNumeroFactura());

        return facturaRepository.save(factura);
    }


    // ─── ANULAR FACTURA ──────────────────────────────────────

    @Transactional
    public Factura anular(Long facturaId) {
        Factura factura = buscarPorId(facturaId);

        if (factura.getEstado() == EstadoFactura.ANULADA) {
            throw new NegocioException("La factura ya está anulada");
        }

        // Solo llama a Siigo en producción (siigoClient != null) y si fue emitida
        if (factura.getEstado() == EstadoFactura.EMITIDA && siigoClient != null) {
            try {
                String token = siigoClient.obtenerToken();
                siigoClient.anularFactura(token, factura.getSiigoId());
            } catch (NegocioException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error inesperado al anular factura {}: {}", factura.getNumero(), e.getMessage(), e);
                throw new NegocioException("Error al anular la factura en la DIAN. Intente nuevamente.");
            }
        }

        factura.setEstado(EstadoFactura.ANULADA);
        return facturaRepository.save(factura);
    }


    // ─── GENERAR NÚMERO ──────────────────────────────────────

    // La consulta usa SELECT ... FOR UPDATE (PESSIMISTIC_WRITE) para que dos
    // transacciones concurrentes no lean el mismo número y generen duplicados.
    private String generarNumero() {
        return facturaRepository.findTopByOrderByIdDesc()
                .map(f -> {
                    try {
                        String raw = f.getNumero().replace(prefijo + "-", "").trim();
                        return prefijo + "-" + (Integer.parseInt(raw) + 1);
                    } catch (NumberFormatException e) {
                        log.warn("Número de factura con formato inesperado '{}', usando ID como base",
                            f.getNumero());
                        return prefijo + "-" + (f.getId() + 1);
                    }
                })
                .orElse(prefijo + "-1479");
    }
}
