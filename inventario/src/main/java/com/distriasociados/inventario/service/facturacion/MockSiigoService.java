package com.distriasociados.inventario.service.facturacion;

import com.distriasociados.inventario.dto.facturacion.FacturaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.NotaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.SiigoConfigDto;
import com.distriasociados.inventario.dto.facturacion.SiigoInvoiceResult;
import com.distriasociados.inventario.dto.facturacion.SiigoProductoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SIMULADOR de Siigo — activo cuando facturacion.proveedor=mock
 *
 * Genera números de factura secuenciales ficticios y registra en consola
 * exactamente lo que enviaría a Siigo en producción.
 * No hace ninguna llamada HTTP real.
 *
 * Para pasar a producción: cambiar facturacion.proveedor=siigo en properties.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "facturacion.proveedor", havingValue = "mock", matchIfMissing = true)
public class MockSiigoService implements SiigoPort {

    private final AtomicLong contador   = new AtomicLong(1);
    private final AtomicLong contadorNC = new AtomicLong(1);
    private final AtomicLong contadorND = new AtomicLong(1);

    @Override
    public SiigoInvoiceResult crearFactura(FacturaSiigoRequest request) {
        String fakeId     = UUID.randomUUID().toString();
        String fakeNumero = String.format("FE-%04d", contador.getAndIncrement());

        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║  [MOCK SIIGO] Simulando envío de factura             ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  Número generado : {}                          ║", fakeNumero);
        log.info("║  Cliente NIT     : {}                   ║", request.getClienteNit());
        log.info("║  Cliente nombre  : {}           ║", request.getClienteNombre());
        log.info("║  Fecha           : {}                      ║", request.getFecha());
        log.info("║  Total           : ${}                     ║", request.getTotal());
        log.info("║  Remisiones      : {}                   ║", request.getRemisionIds());
        log.info("║  Consolidada CF  : {}                             ║", request.isEsConsolidadaConsumidorFinal());
        log.info("║  Líneas: {} productos                              ║", request.getLineas().size());
        request.getLineas().forEach(l ->
            log.info("║    - {} x{} @ ${}/u (IVA {}%)  ║",
                l.getDescripcion(), l.getCantidad(),
                l.getPrecioUnitario(), l.getPorcentajeIva()));
        log.info("╚══════════════════════════════════════════════════════╝");

        return SiigoInvoiceResult.builder()
                .exito(true)
                .siigoId(fakeId)
                .numeroFactura(fakeNumero)
                .build();
    }

    @Override
    public SiigoInvoiceResult crearNotaCredito(NotaSiigoRequest request) {
        String fakeId  = UUID.randomUUID().toString();
        String fakeNum = String.format("NC-%04d", contadorNC.getAndIncrement());
        log.info("[MOCK SIIGO] Nota Crédito {} — Factura: {} — Total: ${} — Motivo: {}",
            fakeNum, request.getFacturaNumero(), request.getTotal(), request.getMotivo());
        return SiigoInvoiceResult.builder().exito(true).siigoId(fakeId).numeroFactura(fakeNum).build();
    }

    @Override
    public List<SiigoProductoDto> listarProductos() {
        log.info("[MOCK SIIGO] listarProductos() — modo mock, no se conecta a Siigo (retorna lista vacía)");
        return List.of();
    }

    @Override
    public SiigoInvoiceResult crearNotaDebito(NotaSiigoRequest request) {
        String fakeId  = UUID.randomUUID().toString();
        String fakeNum = String.format("ND-%04d", contadorND.getAndIncrement());
        log.info("[MOCK SIIGO] Nota Débito {} — Factura: {} — Total: ${} — Motivo: {}",
            fakeNum, request.getFacturaNumero(), request.getTotal(), request.getMotivo());
        return SiigoInvoiceResult.builder().exito(true).siigoId(fakeId).numeroFactura(fakeNum).build();
    }

    @Override
    public SiigoConfigDto consultarConfiguracion() {
        log.info("[MOCK SIIGO] consultarConfiguracion() — modo mock, no se conecta a Siigo");
        return SiigoConfigDto.builder()
            .modoReal(false)
            .mensaje("El servidor está en modo MOCK (facturacion.proveedor=mock). " +
                     "Para consultar los IDs reales cambia la variable de entorno " +
                     "FACTURACION_PROVEEDOR=siigo y recarga.")
            .tiposDocumento(List.of())
            .impuestos(List.of())
            .metodosPago(List.of())
            .build();
    }
}
