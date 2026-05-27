package com.distriasociados.inventario.dto.facturacion;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/** Una línea de producto dentro de la factura a enviar a Siigo */
@Data
@Builder
public class LineaFacturaDto {
    private String codigoProducto;       // Código interno del producto
    private String descripcion;          // Nombre completo del producto
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;   // Sin IVA
    private BigDecimal porcentajeIva;    // 0, 5 o 19
}
