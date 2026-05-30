package com.distriasociados.inventario.service;

import com.distriasociados.inventario.dto.facturacion.*;
import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Remision.EstadoRemision;
import com.distriasociados.inventario.repository.FacturaRepository;
import com.distriasociados.inventario.repository.RemisionRepository;
import com.distriasociados.inventario.service.facturacion.SiigoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Importación del servicio de PDF
import com.distriasociados.inventario.service.PdfService;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturacionElectronicaService {

    private final RemisionRepository remisionRepository;
    private final FacturaRepository  facturaRepository;
    private final SiigoPort siigoPort;
    private final PdfService pdfService;

    /**
     * Inyección opcional: si el servidor de correo no está configurado (ej. dev sin SendGrid),
     * el bean puede no existir y la facturación sigue funcionando sin problema.
     */
    @Autowired(required = false)
    private EmailService emailService;

    // NIT reservado para ventas sin cliente identificado
    private static final String NIT_CONSUMIDOR_FINAL = "222222222222";

    // ─── RESUMEN PREVIO ──────────────────────────────────────────
    // Muestra qué se va a enviar sin hacer nada todavía

    public ResumenFacturacionDto resumenDia(LocalDate fecha) {
        List<Remision> pendientes = remisionRepository.findByFechaAndEstado(fecha, EstadoRemision.PENDIENTE);
        List<Remision> facturadas = remisionRepository.findByFechaAndEstado(fecha, EstadoRemision.FACTURADA);
        List<Remision> conError   = pendientes.stream()
            .filter(r -> r.getErrorFacturacion() != null)
            .collect(Collectors.toList());

        // Separar consumidor final vs clientes identificados
        List<Remision> cf = pendientes.stream()
            .filter(r -> NIT_CONSUMIDOR_FINAL.equals(r.getCliente().getNit()))
            .collect(Collectors.toList());

        List<Remision> identificados = pendientes.stream()
            .filter(r -> !NIT_CONSUMIDOR_FINAL.equals(r.getCliente().getNit()))
            .collect(Collectors.toList());

        List<ResumenFacturacionDto.ItemResumen> items = new ArrayList<>();

        // Ítem consolidado de consumidor final (si hay)
        if (!cf.isEmpty()) {
            BigDecimal totalCF = cf.stream()
                .map(r -> r.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            items.add(ResumenFacturacionDto.ItemResumen.builder()
                .clienteNit(NIT_CONSUMIDOR_FINAL)
                .clienteNombre("CONSUMIDOR FINAL (consolidado)")
                .cantidadRemisiones(cf.size())
                .total(totalCF)
                .esConsolidada(true)
                .build());
        }

        // Un ítem por cada remisión de cliente identificado
        for (Remision r : identificados) {
            items.add(ResumenFacturacionDto.ItemResumen.builder()
                .clienteNit(r.getCliente().getNit())
                .clienteNombre(r.getCliente().getRazonSocial())
                .cantidadRemisiones(1)
                .total(r.getTotal())
                .esConsolidada(false)
                .build());
        }

        BigDecimal totalSinIva = pendientes.stream()
            .map(Remision::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIva = pendientes.stream()
            .map(Remision::getTotalIva).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIbua = pendientes.stream()
            .map(r -> r.getTotalIbua() != null ? r.getTotalIbua() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGeneral = pendientes.stream()
            .map(Remision::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        int facturasAGenerar = items.size();

        return ResumenFacturacionDto.builder()
            .fecha(fecha)
            .totalRemisionesPendientes(pendientes.size())
            .totalRemisionesYaFacturadas(facturadas.size())
            .totalRemisionesConError(conError.size())
            .facturasAGenerar(facturasAGenerar)
            .remisionesConsumidorFinal(cf.size())
            .remisionesClienteIdentificado(identificados.size())
            .totalSinIva(totalSinIva)
            .totalIva(totalIva)
            .totalIbua(totalIbua)
            .totalGeneral(totalGeneral)
            .items(items)
            .build();
    }

    // ─── FACTURAR DÍA ────────────────────────────────────────────
    // Envía todas las remisiones PENDIENTE del día a Siigo

    @Transactional
    public Map<String, Object> facturarDia(LocalDate fecha) {
        List<Remision> pendientes = remisionRepository.findByFechaAndEstado(fecha, EstadoRemision.PENDIENTE);

        if (pendientes.isEmpty()) {
            return Map.of(
                "enviadas", 0,
                "errores", 0,
                "mensaje", "No hay remisiones pendientes para " + fecha
            );
        }

        // Separar por tipo de cliente
        List<Remision> cf = pendientes.stream()
            .filter(r -> NIT_CONSUMIDOR_FINAL.equals(r.getCliente().getNit()))
            .collect(Collectors.toList());

        List<Remision> identificados = pendientes.stream()
            .filter(r -> !NIT_CONSUMIDOR_FINAL.equals(r.getCliente().getNit()))
            .collect(Collectors.toList());

        int enviadas = 0;
        int errores  = 0;

        // ── 1. Factura consolidada de consumidor final ──
        if (!cf.isEmpty()) {
            FacturaSiigoRequest request = construirConsolidadaCF(cf, fecha);
            SiigoInvoiceResult result = siigoPort.crearFactura(request);

            if (result.isExito()) {
                // Crear UNA sola Factura compartida por todas las remisiones CF
                crearYVincularFactura(cf, result, fecha);
                cf.forEach(r -> marcarFacturada(r, result.getSiigoId(), result.getNumeroFactura()));
                // CF consolidado: enviar correo por cada remisión (el guard de envioCorreo filtrará las que no apliquen)
                if (emailService != null) {
                    cf.forEach(r -> emailService.enviarFacturaElectronica(r));
                }
                enviadas++;
                log.info("[FACTURACION] CF consolidado {} → {} remisiones → {}",
                    fecha, cf.size(), result.getNumeroFactura());
            } else {
                cf.forEach(r -> marcarError(r, result.getError()));
                errores++;
                log.error("[FACTURACION] Error CF consolidado: {}", result.getError());
            }
        }

        // ── 2. Una factura por cada remisión de cliente identificado ──
        for (Remision remision : identificados) {
            FacturaSiigoRequest request = construirFacturaIndividual(remision);
            SiigoInvoiceResult result = siigoPort.crearFactura(request);

            if (result.isExito()) {
                crearYVincularFactura(List.of(remision), result, remision.getFecha());
                marcarFacturada(remision, result.getSiigoId(), result.getNumeroFactura());
                // Enviar factura por correo al cliente (asíncrono, no bloquea)
                if (emailService != null) {
                    emailService.enviarFacturaElectronica(remision);
                }
                enviadas++;
                log.info("[FACTURACION] {} → {} → {}",
                    remision.getNumero(), remision.getCliente().getNit(), result.getNumeroFactura());
            } else {
                marcarError(remision, result.getError());
                errores++;
                log.error("[FACTURACION] Error {}: {}", remision.getNumero(), result.getError());
            }
        }

        remisionRepository.flush();

        return Map.of(
            "fecha", fecha.toString(),
            "remisionesProcesadas", pendientes.size(),
            "facturasEnviadas", enviadas,
            "errores", errores,
            "mensaje", errores == 0
                ? "Facturación completada sin errores"
                : errores + " factura(s) con error — revisa el detalle"
        );
    }

    // ─── REINTENTAR UNA REMISION CON ERROR ───────────────────────

    @Transactional
    public Map<String, Object> reintentarRemision(Long remisionId) {
        Remision remision = remisionRepository.findById(remisionId)
            .orElseThrow(() -> new RuntimeException("Remisión no encontrada: " + remisionId));

        if (remision.getEstado() == EstadoRemision.FACTURADA) {
            return Map.of("mensaje", "La remisión ya fue facturada: " + remision.getNumeroFacturaDian());
        }

        FacturaSiigoRequest request = construirFacturaIndividual(remision);
        SiigoInvoiceResult result = siigoPort.crearFactura(request);

        if (result.isExito()) {
            crearYVincularFactura(List.of(remision), result, remision.getFecha());
            marcarFacturada(remision, result.getSiigoId(), result.getNumeroFactura());
            // Reenviar correo también en reintentos exitosos
            if (emailService != null) {
                emailService.enviarFacturaElectronica(remision);
            }
            return Map.of(
                "exito", true,
                "numeroFactura", result.getNumeroFactura(),
                "mensaje", "Factura enviada correctamente"
            );
        } else {
            marcarError(remision, result.getError());
            return Map.of(
                "exito", false,
                "error", result.getError(),
                "mensaje", "Error al enviar la factura"
            );
        }
    }

    // ─── SINCRONIZAR FACTURAS HISTÓRICAS ─────────────────────────
    /**
     * Crea registros Factura para remisiones que ya fueron facturadas
     * antes de que se implementara el vínculo automático.
     * Es idempotente — puede llamarse varias veces sin duplicar.
     */
    @Transactional
    public Map<String, Object> sincronizarFacturasExistentes() {
        List<Remision> sinFactura = remisionRepository.findFacturadasSinFactura();
        if (sinFactura.isEmpty()) {
            return Map.of("mensaje", "Todas las remisiones ya tienen factura vinculada", "creadas", 0);
        }

        // Agrupar por numeroFacturaDian: las remisiones CF comparten el mismo número
        Map<String, List<Remision>> grupos = sinFactura.stream()
            .collect(Collectors.groupingBy(
                r -> r.getNumeroFacturaDian() != null ? r.getNumeroFacturaDian() : "REM-" + r.getId()
            ));

        int creadas = 0;
        int vinculadas = 0;
        for (Map.Entry<String, List<Remision>> entry : grupos.entrySet()) {
            List<Remision> grupo = entry.getValue();
            Remision primera    = grupo.get(0);
            String numeroFactura = primera.getNumeroFacturaDian();

            // Si la factura ya existe en BD, solo vincular las remisiones
            Optional<Factura> facturaExistente = facturaRepository.findByNumero(numeroFactura);
            if (facturaExistente.isPresent()) {
                Factura factura = facturaExistente.get();
                for (Remision r : grupo) {
                    r.setFactura(factura);
                    remisionRepository.save(r);
                }
                vinculadas++;
                log.info("[SYNC] Factura {} ya existía — remisiones vinculadas: {}", numeroFactura, grupo.size());
                continue;
            }

            // No existe → crear y vincular
            SiigoInvoiceResult syntheticResult = SiigoInvoiceResult.builder()
                .exito(true)
                .siigoId(primera.getSiigoInvoiceId() != null ? primera.getSiigoInvoiceId() : "SYNC-" + primera.getId())
                .numeroFactura(numeroFactura)
                .build();

            crearYVincularFactura(grupo, syntheticResult, primera.getFecha());
            creadas++;
            log.info("[SYNC] Factura creada para {} ({} remision/es)", numeroFactura, grupo.size());
        }

        return Map.of(
            "mensaje", "Sincronización completada",
            "remisionesSinFactura", sinFactura.size(),
            "facturasCreadas", creadas,
            "facturasVinculadas", vinculadas
        );
    }

    // ─── HELPERS PRIVADOS ────────────────────────────────────────

    /** Construye el request consolidado de todas las remisiones de CF del día */
    private FacturaSiigoRequest construirConsolidadaCF(List<Remision> remisiones, LocalDate fecha) {
        List<LineaFacturaDto> lineas = consolidarLineas(remisiones);
        List<Long> ids = remisiones.stream().map(Remision::getId).collect(Collectors.toList());

        BigDecimal subtotal = remisiones.stream().map(Remision::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal iva      = remisiones.stream().map(Remision::getTotalIva).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total    = remisiones.stream().map(Remision::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Nombre del CF: tomar de la primera remisión
        String nombreCF = remisiones.get(0).getCliente().getRazonSocial();

        return FacturaSiigoRequest.builder()
            .clienteNit(NIT_CONSUMIDOR_FINAL)
            .clienteNombre(nombreCF)
            .clienteTipoPersona("NATURAL")
            .fecha(fecha)
            .lineas(lineas)
            .totalSinIva(subtotal)
            .totalIva(iva)
            .total(total)
            .remisionIds(ids)
            .esConsolidadaConsumidorFinal(true)
            .build();
    }

    /** Construye el request individual de una remisión de cliente identificado */
    private FacturaSiigoRequest construirFacturaIndividual(Remision remision) {
        List<LineaFacturaDto> lineas = remision.getDetalles().stream()
            .map(d -> LineaFacturaDto.builder()
                // Siigo requiere el SKU (siigoCodigo); fallback al código local si no está asignado
                .codigoProducto(d.getProducto().getSiigoCodigo() != null
                    ? d.getProducto().getSiigoCodigo() : d.getProducto().getCodigo())
                .descripcion(d.getDescripcionProducto())
                .cantidad(d.getCantidad())
                .precioUnitario(d.getPrecioUnitario())
                .porcentajeIva(d.getPorcentajeIva())
                .build())
            .collect(Collectors.toList());

        return FacturaSiigoRequest.builder()
            .clienteNit(remision.getCliente().getNit())
            .clienteNombre(remision.getCliente().getRazonSocial())
            .clienteTipoPersona(remision.getCliente().getTipoPersona().name())
            .fecha(remision.getFecha())
            .lineas(lineas)
            .totalSinIva(remision.getSubtotal())
            .totalIva(remision.getTotalIva())
            .total(remision.getTotal())
            .remisionIds(List.of(remision.getId()))
            .esConsolidadaConsumidorFinal(false)
            .build();
    }

    /**
     * Consolida detalles de múltiples remisiones agrupando por producto.
     * Si el mismo producto aparece en varias remisiones de CF, se suman las cantidades.
     */
    private List<LineaFacturaDto> consolidarLineas(List<Remision> remisiones) {
        Map<String, LineaConsolidada> mapa = new LinkedHashMap<>();

        for (Remision r : remisiones) {
            for (RemisionDetalle d : r.getDetalles()) {
                // Clave de agrupación: siigoCodigo si existe, sino código local
                String siigoCod = d.getProducto().getSiigoCodigo() != null
                    ? d.getProducto().getSiigoCodigo() : d.getProducto().getCodigo();
                String key = siigoCod;
                if (mapa.containsKey(key)) {
                    mapa.get(key).cantidad = mapa.get(key).cantidad.add(d.getCantidad());
                } else {
                    LineaConsolidada lc = new LineaConsolidada();
                    lc.codigo       = siigoCod;   // SKU de Siigo para la factura
                    lc.descripcion  = d.getDescripcionProducto();
                    lc.cantidad     = d.getCantidad();
                    lc.precioUnitario = d.getPrecioUnitario();
                    lc.porcentajeIva  = d.getPorcentajeIva();
                    mapa.put(key, lc);
                }
            }
        }

        return mapa.values().stream()
            .map(lc -> LineaFacturaDto.builder()
                .codigoProducto(lc.codigo)
                .descripcion(lc.descripcion)
                .cantidad(lc.cantidad)
                .precioUnitario(lc.precioUnitario)
                .porcentajeIva(lc.porcentajeIva)
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Crea un registro Factura a partir de una o varias remisiones (CF consolidado),
     * copia las líneas de detalle y vincula cada remisión al registro creado.
     */
    private Factura crearYVincularFactura(List<Remision> remisiones, SiigoInvoiceResult result, LocalDate fecha) {
        Remision primera = remisiones.get(0);

        // Calcular totales
        BigDecimal subtotal = remisiones.stream().map(Remision::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIva = remisiones.stream().map(Remision::getTotalIva).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total    = remisiones.stream().map(Remision::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Mapear FormaPago de Remision → Factura
        Factura.FormaPago fp = primera.getFormaPago() == Remision.FormaPago.CREDITO
            ? Factura.FormaPago.CREDITO
            : Factura.FormaPago.CONTADO;

        // Construir líneas de detalle copiando desde las remisiones
        List<FacturaDetalle> detalles = new ArrayList<>();
        for (Remision r : remisiones) {
            for (RemisionDetalle rd : r.getDetalles()) {
                detalles.add(FacturaDetalle.builder()
                    .descripcion(rd.getDescripcionProducto())
                    .producto(rd.getProducto())
                    .cantidad(rd.getCantidad())
                    .precioUnitario(rd.getPrecioUnitario())
                    .porcentajeIva(rd.getPorcentajeIva() != null ? rd.getPorcentajeIva() : BigDecimal.ZERO)
                    .descuento(BigDecimal.ZERO)
                    .build());
            }
        }

        Factura factura = Factura.builder()
            .numero(result.getNumeroFactura())
            .cliente(primera.getCliente())
            .fecha(fecha)
            .formaPago(fp)
            .medioPago(Factura.MedioPago.EFECTIVO)   // valor por defecto requerido por la BD
            .subtotal(subtotal)
            .descuento(BigDecimal.ZERO)              // NOT NULL en BD
            .totalIva(totalIva)
            .total(total)
            .estado(Factura.EstadoFactura.EMITIDA)
            .siigoId(result.getSiigoId())
            .build();

        // Asignar back-reference a cada detalle y añadir a la factura
        for (FacturaDetalle det : detalles) {
            det.setFactura(factura);
        }
        factura.getDetalles().addAll(detalles);

        Factura saved = facturaRepository.save(factura);

        // Vincular cada remisión a esta factura
        for (Remision r : remisiones) {
            r.setFactura(saved);
            remisionRepository.save(r);
        }

        return saved;
    }

    private void marcarFacturada(Remision r, String siigoId, String numero) {
        r.setEstado(EstadoRemision.FACTURADA);
        r.setSiigoInvoiceId(siigoId);
        r.setNumeroFacturaDian(numero);
        r.setFechaFacturacion(LocalDateTime.now());
        r.setErrorFacturacion(null);
        remisionRepository.save(r);
    }

    private void marcarError(Remision r, String error) {
        r.setErrorFacturacion(error != null ? error : "Error desconocido");
        remisionRepository.save(r);
    }

    // Clase auxiliar para consolidar líneas
    private static class LineaConsolidada {
        String codigo, descripcion;
        BigDecimal cantidad, precioUnitario, porcentajeIva;
    }

    // ─── PENDIENTES DEL DÍA (para vista previa detallada) ───────

    public List<Remision> pendientesDia(LocalDate fecha) {
        return remisionRepository.findByFechaAndEstado(fecha, EstadoRemision.PENDIENTE);
    }

    // ─── HISTORIAL ───────────────────────────────────────────────

    /**
     * Devuelve remisiones que han sido procesadas por facturación:
     * - Estado FACTURADA (enviadas con éxito)
     * - O que tienen errorFacturacion registrado (intentos fallidos)
     * Filtrado por rango de fechas opcional.
     */
    public List<Remision> historial(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null)    fin    = LocalDate.now();
        return remisionRepository
            .findByFechaBetweenAndFacturadas(inicio, fin);
    }

    // ─── PDF FACTURA DIAN ────────────────────────────────────────

    /**
     * Genera el PDF de la factura electrónica para una remisión.
     * En modo mock: genera el PDF localmente en formato Siigo.
     * En producción: podría descargar el PDF directamente de Siigo.
     */
    public byte[] generarPdfFactura(Long remisionId) {
        Remision remision = remisionRepository.findById(remisionId)
            .orElseThrow(() -> new RuntimeException("Remisión no encontrada: " + remisionId));

        if (remision.getEstado() != EstadoRemision.FACTURADA) {
            throw new RuntimeException(
                "La remisión " + remision.getNumero() + " aún no ha sido facturada");
        }

        return pdfService.generarFacturaDian(remision);
    }

    public String nombrePdfFactura(Long remisionId) {
        Remision remision = remisionRepository.findById(remisionId)
            .orElseThrow(() -> new RuntimeException("Remisión no encontrada: " + remisionId));
        String num = remision.getNumeroFacturaDian() != null
            ? remision.getNumeroFacturaDian().replace("/", "-")
            : "remision-" + remisionId;
        return "factura_" + num + ".pdf";
    }
}
