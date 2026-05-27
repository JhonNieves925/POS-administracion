package com.distriasociados.inventario.dto.facturacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * Datos de un producto tal como los devuelve la API de Siigo.
 * GET /v1/products
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiigoProductoDto {

    /** ID interno de Siigo (codificado, ej: "CjQxNTc0") */
    private String siigoId;

    /** Código del producto en Siigo — es el que se envía en las líneas de factura */
    private String codigo;

    /** Nombre completo tal como aparece en Siigo */
    private String nombre;

    /** Porcentaje de IVA (0, 5 o 19) */
    private double porcentajeIva;

    /** Precio de lista (COP) — puede ser cero si no está configurado */
    private BigDecimal precio;

    /** Si el producto está activo en Siigo */
    private boolean activo;

    /**
     * Flag calculado en backend: true si el producto ya existe en la BD local
     * (ya sea por siigoCodigo o por codigo local que coincide).
     * No viene de Siigo — se rellena en SiigoAdminController.
     */
    private boolean yaEnSistema;
}
