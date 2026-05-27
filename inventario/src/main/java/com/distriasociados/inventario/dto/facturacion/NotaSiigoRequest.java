package com.distriasociados.inventario.dto.facturacion;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO para enviar una Nota Crédito o Débito a Siigo.
 */
@Data
@Builder
public class NotaSiigoRequest {

    private String tipoNota;          // "credito" o "debito"
    private String facturaNumero;     // Número de la factura original  Ej: FEV-001
    private String facturaSiigoId;    // ID de la factura en Siigo
    private String clienteNit;
    private LocalDate fecha;
    private String motivo;
    private BigDecimal total;
    private List<LineaFacturaDto> lineas;
}
