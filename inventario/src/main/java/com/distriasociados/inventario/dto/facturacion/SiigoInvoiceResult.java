package com.distriasociados.inventario.dto.facturacion;

import lombok.Builder;
import lombok.Data;

/** Resultado que devuelve Siigo (real o mock) al crear una factura */
@Data
@Builder
public class SiigoInvoiceResult {
    private boolean exito;
    private String siigoId;         // UUID interno de Siigo
    private String numeroFactura;   // Ej: FE-0001
    private String cufe;            // Código único DIAN (campo "stamp" en respuesta Siigo)
    private String error;           // Mensaje de error si exito=false
}
