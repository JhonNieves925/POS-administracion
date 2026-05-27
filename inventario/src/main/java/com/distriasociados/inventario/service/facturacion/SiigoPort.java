package com.distriasociados.inventario.service.facturacion;

import com.distriasociados.inventario.dto.facturacion.FacturaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.NotaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.SiigoConfigDto;
import com.distriasociados.inventario.dto.facturacion.SiigoInvoiceResult;
import com.distriasociados.inventario.dto.facturacion.SiigoProductoDto;
import java.util.List;

/**
 * Contrato de comunicación con Siigo.
 * Implementaciones:
 *   - MockSiigoService   → desarrollo / pruebas (sin credenciales)
 *   - RealSiigoService   → producción (con credenciales reales)
 *
 * Para cambiar entre modos: facturacion.proveedor=mock|siigo en application.properties
 */
public interface SiigoPort {

    /** Envía una factura a Siigo. Nunca lanza excepción. */
    SiigoInvoiceResult crearFactura(FacturaSiigoRequest request);

    /** Envía una nota crédito a Siigo referenciando la factura original. */
    SiigoInvoiceResult crearNotaCredito(NotaSiigoRequest request);

    /** Envía una nota débito a Siigo referenciando la factura original. */
    SiigoInvoiceResult crearNotaDebito(NotaSiigoRequest request);

    /**
     * Obtiene el catálogo completo de productos activos desde Siigo.
     * RealSiigoService: pagina hasta traer todos.
     * MockSiigoService: retorna lista vacía.
     */
    List<SiigoProductoDto> listarProductos();

    /**
     * Consulta los IDs de configuración de la cuenta Siigo:
     * tipos de documento, impuestos y métodos de pago.
     * Se usa una sola vez para configurar las variables de entorno en producción.
     * MockSiigoService: retorna DTO con modoReal=false y mensaje explicativo.
     */
    SiigoConfigDto consultarConfiguracion();
}
